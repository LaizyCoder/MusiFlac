package gobackend

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/dop251/goja"
)

const extensionMasterKeyFile = ".master_key"

// Version preserves the old MusiFlac API.
func Version() string {
	return "MusiFlac Go Backend 0.1"
}

// EvaluateJavaScript preserves the old runtime test API.
func EvaluateJavaScript(script string) (string, error) {
	vm := goja.New()

	value, err := vm.RunString(script)
	if err != nil {
		return "", err
	}

	return value.String(), nil
}

// ensureExtensionSystem initializes the official runtime exactly once per
// process. The master key is persisted in the application's private data
// directory so extension settings and credentials remain decryptable across
// application restarts.
func ensureExtensionSystem(extensionsDir string) error {
	dataDir := filepath.Join(extensionsDir, ".data")

	if err := os.MkdirAll(dataDir, 0700); err != nil {
		return fmt.Errorf("failed to create extension data directory: %w", err)
	}

	keyPath := filepath.Join(dataDir, extensionMasterKeyFile)

	key, err := os.ReadFile(keyPath)
	if err != nil {
		if !os.IsNotExist(err) {
			return fmt.Errorf("failed to read extension storage key: %w", err)
		}

		rawKey := make([]byte, extensionStorageMasterKeyBytes)

		if _, err := rand.Read(rawKey); err != nil {
			return fmt.Errorf("failed to generate extension storage key: %w", err)
		}

		encoded := base64.StdEncoding.EncodeToString(rawKey)

		if err := os.WriteFile(keyPath, []byte(encoded), 0600); err != nil {
			return fmt.Errorf("failed to persist extension storage key: %w", err)
		}

		key = []byte(encoded)
	}

	if err := SetExtensionStorageMasterKey(string(key)); err != nil {
		return err
	}

	if err := InitExtensionSystem(extensionsDir, dataDir); err != nil {
		return err
	}

	return nil
}

// LoadExtension preserves the old Kotlin API while using the official
// extension manager.
func LoadExtension(packagePath string) (string, error) {
	extensionDir := filepath.Dir(packagePath)

	if err := ensureExtensionSystem(extensionDir); err != nil {
		return "", err
	}

	return LoadExtensionFromPath(packagePath)
}

// LoadExtensionsFromDirectory preserves the old Kotlin API while using
// the official extension manager.
func LoadExtensionsFromDirectory(directoryPath string) (string, error) {
	if err := ensureExtensionSystem(directoryPath); err != nil {
		return "", err
	}

	return LoadExtensionsFromDir(directoryPath)
}

// GetExtensionMethods returns callable methods exposed by an extension.
func GetExtensionMethods(extensionID string) (string, error) {
	manager := getExtensionManager()

	ext, err := manager.GetExtension(extensionID)
	if err != nil {
		return "", err
	}

	vm, err := ext.lockReadyVM()
	if err != nil {
		return "", err
	}
	defer ext.VMMu.Unlock()

	result := make([]string, 0)

	extensionValue := vm.Get("extension")

	if !gojaValueIsEmpty(extensionValue) {
		extensionObject := extensionValue.ToObject(vm)

		for _, key := range extensionObject.Keys() {
			value := extensionObject.Get(key)

			if value == nil || value == goja.Undefined() {
				continue
			}

			if _, ok := goja.AssertFunction(value); ok {
				result = append(result, key)
			}
		}
	}

	for _, key := range vm.GlobalObject().Keys() {
		value := vm.Get(key)

		if value == nil || value == goja.Undefined() {
			continue
		}

		if _, ok := goja.AssertFunction(value); ok {
			found := false

			for _, existing := range result {
				if existing == key {
					found = true
					break
				}
			}

			if !found {
				result = append(result, key)
			}
		}
	}

	data, err := json.Marshal(result)
	if err != nil {
		return "", fmt.Errorf(
			"unable to serialize extension methods: %w",
			err,
		)
	}

	return string(data), nil
}

// GetExtensionMethod preserves the old singular Kotlin API.
func GetExtensionMethod(extensionID string, method string) bool {
	methodsJSON, err := GetExtensionMethods(extensionID)
	if err != nil {
		return false
	}

	var methods []string

	if err := json.Unmarshal([]byte(methodsJSON), &methods); err != nil {
		return false
	}

	for _, candidate := range methods {
		if candidate == method {
			return true
		}
	}

	return false
}

// CallExtensionMethod preserves the old Kotlin API while routing execution
// through the official Goja runtime.
func CallExtensionMethod(
	extensionID string,
	method string,
	argumentsJSON string,
) (string, error) {

	if argumentsJSON == "" {
		argumentsJSON = "[]"
	}

	var rawArguments []json.RawMessage

	if err := json.Unmarshal([]byte(argumentsJSON), &rawArguments); err != nil {
		return "", fmt.Errorf(
			"invalid extension arguments: %w",
			err,
		)
	}

	arguments := make([]any, 0, len(rawArguments))

	for _, raw := range rawArguments {
		var value any

		if err := json.Unmarshal(raw, &value); err != nil {
			return "", fmt.Errorf(
				"unable to decode extension argument: %w",
				err,
			)
		}

		arguments = append(arguments, value)
	}

	manager := getExtensionManager()

	ext, err := manager.GetExtension(extensionID)
	if err != nil {
		return "", err
	}

	opts := extCallOpts{
		timeout:  60 * time.Second,
		perfName: method,
		invoke:   extensionMethodInvocation(method, arguments...),
	}

	provider := &extensionProviderWrapper{
		extension: ext,
		vm:        nil,
	}

	result, err := callExtension(
		provider,
		opts,
		func(_ *extensionCallPerf, value goja.Value) (string, error) {
			if value == nil ||
				value == goja.Undefined() ||
				value == goja.Null() {
				return "null", nil
			}

			exported := value.Export()

			data, err := json.Marshal(exported)
			if err != nil {
				return "", fmt.Errorf(
					"unable to serialize extension result: %w",
					err,
				)
			}

			return string(data), nil
		},
	)

	if err != nil {
		return "", err
	}

	return result, nil
}

// HasExtensionMethod preserves the old Kotlin API.
func HasExtensionMethod(extensionID string, method string) bool {
	return GetExtensionMethod(extensionID, method)
}
