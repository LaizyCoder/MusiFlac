package gobackend

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/dop251/goja"
)

func Version() string {
	return "MusiFlac Go Backend 0.1"
}

func EvaluateJavaScript(script string) (string, error) {
	vm := goja.New()

	value, err := vm.RunString(script)
	if err != nil {
		return "", err
	}

	return value.String(), nil
}

func GetExtensionMethods(
	extensionID string,
) (string, error) {

	extensionsMu.RLock()

	extension :=
		extensions[extensionID]

	extensionsMu.RUnlock()

	if extension == nil {
		return "", fmt.Errorf(
			"extension %q is not loaded",
			extensionID,
		)
	}

	methods :=
		make([]string, 0)

	for _, key := range extension.Provider.Keys() {

		value :=
			extension.Provider.Get(key)

		if value == nil ||
			value == goja.Undefined() {

			continue
		}

		if _, ok :=
			goja.AssertFunction(value); ok {

			methods =
				append(
					methods,
					key,
				)
		}
	}

	result, err :=
		json.Marshal(methods)

	if err != nil {
		return "", fmt.Errorf(
			"unable to serialize extension methods: %w",
			err,
		)
	}

	return string(result), nil
}

func CallExtensionMethod(
	extensionID string,
	method string,
	argumentsJSON string,
) (string, error) {

	extensionsMu.RLock()
	extension := extensions[extensionID]
	extensionsMu.RUnlock()

	if extension == nil {
		return "", fmt.Errorf(
			"extension %q is not loaded",
			extensionID,
		)
	}

	if extension.EventLoop == nil {
		return "", fmt.Errorf(
			"extension %q has no JavaScript event loop",
			extensionID,
		)
	}

	var rawArguments []json.RawMessage

	if argumentsJSON == "" {
		argumentsJSON = "[]"
	}

	if err := json.Unmarshal(
		[]byte(argumentsJSON),
		&rawArguments,
	); err != nil {
		return "", fmt.Errorf(
			"invalid extension arguments: %w",
			err,
		)
	}

	resultChannel := make(chan struct {
		result string
		err    error
	}, 1)

	extension.EventLoop.RunOnLoop(
		func(runtime *goja.Runtime) {

			functionValue :=
				extension.Provider.Get(method)

			if functionValue == nil ||
				functionValue == goja.Undefined() {

				resultChannel <- struct {
					result string
					err    error
				}{
					err: fmt.Errorf(
						"extension %q does not provide method %q",
						extensionID,
						method,
					),
				}

				return
			}

			function, ok :=
				goja.AssertFunction(functionValue)

			if !ok {
				resultChannel <- struct {
					result string
					err    error
				}{
					err: fmt.Errorf(
						"extension %q member %q is not a function",
						extensionID,
						method,
					),
				}

				return
			}

			arguments :=
				make([]goja.Value, 0, len(rawArguments))

			for _, rawArgument := range rawArguments {

				var value interface{}

				if err := json.Unmarshal(
					rawArgument,
					&value,
				); err != nil {

					resultChannel <- struct {
						result string
						err    error
					}{
						err: fmt.Errorf(
							"unable to decode extension argument: %w",
							err,
						),
					}

					return
				}

				arguments =
					append(
						arguments,
						runtime.ToValue(value),
					)
			}

			result, err :=
				function(
					goja.Undefined(),
					arguments...,
				)

			if err != nil {
				resultChannel <- struct {
					result string
					err    error
				}{
					err: fmt.Errorf(
						"extension method %q failed: %w",
						method,
						err,
					),
				}

				return
			}

			if result == nil ||
				result == goja.Undefined() {

				resultChannel <- struct {
					result string
					err    error
				}{
					result: "null",
				}

				return
			}

			object :=
				result.ToObject(runtime)

			thenValue :=
				object.Get("then")

			/*
			 * Synchronous result.
			 */
			if thenValue == nil ||
				thenValue == goja.Undefined() {

				exported :=
					result.Export()

				resultJSON, err :=
					json.Marshal(exported)

				if err != nil {
					resultChannel <- struct {
						result string
						err    error
					}{
						err: fmt.Errorf(
							"unable to serialize extension result: %w",
							err,
						),
					}

					return
				}

				resultChannel <- struct {
					result string
					err    error
				}{
					result: string(resultJSON),
				}

				return
			}

			/*
			 * Promise / Promise-like result.
			 *
			 * The handlers execute on the same Goja event loop,
			 * so the resolved JavaScript value is exported only
			 * from the JavaScript runtime thread.
			 */
			thenFunction, ok :=
				goja.AssertFunction(thenValue)

			if !ok {
				resultChannel <- struct {
					result string
					err    error
				}{
					err: fmt.Errorf(
						"extension method %q returned an invalid Promise-like value",
						method,
					),
				}

				return
			}

			resolveCallback :=
				runtime.ToValue(
					func(call goja.FunctionCall) goja.Value {

						resolved :=
							goja.Undefined()

						if len(call.Arguments) > 0 {
							resolved =
								call.Argument(0)
						}

						if resolved == nil ||
							resolved == goja.Undefined() {

							resultChannel <- struct {
								result string
								err    error
							}{
								result: "null",
							}

							return goja.Undefined()
						}

						exported :=
							resolved.Export()

						resultJSON, err :=
							json.Marshal(exported)

						if err != nil {
							resultChannel <- struct {
								result string
								err    error
							}{
								err: fmt.Errorf(
									"unable to serialize resolved extension result: %w",
									err,
								),
							}

							return goja.Undefined()
						}

						resultChannel <- struct {
							result string
							err    error
						}{
							result: string(resultJSON),
						}

						return goja.Undefined()
					},
				)

			rejectCallback :=
				runtime.ToValue(
					func(call goja.FunctionCall) goja.Value {

						reason :=
							"unknown Promise rejection"

						if len(call.Arguments) > 0 {
							reason =
								call.Argument(0).String()
						}

						resultChannel <- struct {
							result string
							err    error
						}{
							err: fmt.Errorf(
								"extension method %q Promise rejected: %s",
								method,
								reason,
							),
						}

						return goja.Undefined()
					},
				)

			_, err =
				thenFunction(
					result,
					resolveCallback,
					rejectCallback,
				)

			if err != nil {
				resultChannel <- struct {
					result string
					err    error
				}{
					err: fmt.Errorf(
						"unable to attach Promise handlers to extension method %q: %w",
						method,
						err,
					),
				}
			}
		},
	)

	select {
	case response := <-resultChannel:
		return response.result, response.err

	case <-time.After(60 * time.Second):
		return "", fmt.Errorf(
			"extension method %q timed out after 60 seconds",
			method,
		)
	}
}
