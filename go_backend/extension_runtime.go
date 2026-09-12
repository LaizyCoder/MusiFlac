package gobackend

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"crypto/hmac"
	"crypto/md5"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"encoding/base64"
	"github.com/dop251/goja"
	"github.com/dop251/goja_nodejs/eventloop"
	"math/big"
)

type extensionManifest struct {
	Name        string   `json:"name"`
	DisplayName string   `json:"displayName"`
	Version     string   `json:"version"`
	Description string   `json:"description"`
	Type        []string `json:"type"`
	Permissions struct {
		Network []string `json:"network"`
	} `json:"permissions"`
}

type loadedExtension struct {
	ID         string
	Manifest   extensionManifest
	Runtime    *goja.Runtime
	Provider   *goja.Object
	Client     *http.Client
	EventLoop  *eventloop.EventLoop
	StorageDir string
}

var (
	extensionsMu sync.RWMutex
	extensions   = make(map[string]*loadedExtension)
)

func LoadExtension(packagePath string) (string, error) {
	manifestBytes, scriptBytes, err := readExtensionPackage(packagePath)
	if err != nil {
		return "", err
	}

	var manifest extensionManifest

	if err := json.Unmarshal(manifestBytes, &manifest); err != nil {
		return "", fmt.Errorf(
			"invalid manifest.json: %w",
			err,
		)
	}

	if manifest.Name == "" {
		return "", fmt.Errorf(
			"extension manifest is missing name",
		)
	}

	if manifest.Version == "" {
		return "", fmt.Errorf(
			"extension manifest is missing version",
		)
	}

	storageDir := filepath.Join(
		filepath.Dir(packagePath),
		"storage",
	)

	if err := os.MkdirAll(storageDir, 0o755); err != nil {
		return "", fmt.Errorf(
			"unable to create extension storage directory: %w",
			err,
		)
	}

	storageFile := filepath.Join(
		storageDir,
		manifest.Name+".json",
	)

	storageData := make(map[string]interface{})

	if data, readErr := os.ReadFile(storageFile); readErr == nil {
		if len(data) > 0 {
			if err := json.Unmarshal(data, &storageData); err != nil {
				storageData = make(map[string]interface{})
			}
		}
	} else if !os.IsNotExist(readErr) {
		return "", fmt.Errorf(
			"unable to read extension storage: %w",
			readErr,
		)
	}

	storageMu := sync.Mutex{}

	eventLoop := eventloop.NewEventLoop()

	var runtime *goja.Runtime
	var registeredProvider *goja.Object

	var httpClient *http.Client

	eventLoop.Run(func(vm *goja.Runtime) {
		runtime = vm

		/*
		 * Each extension gets its own HTTP cookie jar.
		 */
		cookieJar, err := newCookieJar()
		if err != nil {
			panic(
				runtime.ToValue(
					fmt.Sprintf(
						"unable to create HTTP cookie jar: %v",
						err,
					),
				),
			)
		}

		httpClient = &http.Client{
			Jar: cookieJar,

			/*
			 * SpotiFLAC follows redirects automatically.
			 * Go's HTTP client already handles the standard
			 * 301/302/303/307/308 redirect behavior.
			 */
			CheckRedirect: func(
				request *http.Request,
				via []*http.Request,
			) error {

				if len(via) >= 10 {
					return fmt.Errorf(
						"too many HTTP redirects",
					)
				}

				return nil
			},

			Timeout: 30 * time.Second,
		}

		/*
		 * ---------------------------------------------------------
		 * Logging API
		 * ---------------------------------------------------------
		 */

		logObject := runtime.NewObject()

		logObject.Set(
			"debug",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][DEBUG] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		logObject.Set(
			"info",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][INFO] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		logObject.Set(
			"warn",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][WARN] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		logObject.Set(
			"error",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][ERROR] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		runtime.Set(
			"log",
			logObject,
		)

		/*
		 * ---------------------------------------------------------
		 * Console compatibility
		 * ---------------------------------------------------------
		 */

		consoleObject := runtime.NewObject()

		consoleObject.Set(
			"log",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][LOG] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		consoleObject.Set(
			"debug",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][DEBUG] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		consoleObject.Set(
			"info",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][INFO] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		consoleObject.Set(
			"warn",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][WARN] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		consoleObject.Set(
			"error",
			func(call goja.FunctionCall) goja.Value {
				fmt.Printf(
					"[MusiFlac Extension][ERROR] %s: %s\n",
					manifest.Name,
					formatLogArguments(call.Arguments),
				)

				return goja.Undefined()
			},
		)

		runtime.Set(
			"console",
			consoleObject,
		)

		/*
		 * ---------------------------------------------------------
		 * HTTP API
		 * ---------------------------------------------------------
		 */

		storageObject := runtime.NewObject()

		storageObject.Set(
			"get",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					return goja.Null()
				}

				key := call.Argument(0).String()

				storageMu.Lock()
				value, exists := storageData[key]
				storageMu.Unlock()

				if !exists {
					return goja.Null()
				}

				return runtime.ToValue(value)
			},
		)

		storageObject.Set(
			"set",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) < 2 {
					panic(
						runtime.ToValue(
							"storage.set requires key and value",
						),
					)
				}

				key := call.Argument(0).String()
				value := call.Argument(1).Export()

				storageMu.Lock()

				storageData[key] = value
				data, err := json.Marshal(storageData)

				if err == nil {
					err = os.WriteFile(
						storageFile,
						data,
						0o600,
					)
				}

				storageMu.Unlock()

				if err != nil {
					panic(
						runtime.ToValue(
							fmt.Sprintf(
								"storage.set failed: %v",
								err,
							),
						),
					)
				}

				return goja.Undefined()
			},
		)

		storageObject.Set(
			"remove",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					return goja.Undefined()
				}

				key := call.Argument(0).String()

				storageMu.Lock()

				delete(storageData, key)
				data, err := json.Marshal(storageData)

				if err == nil {
					err = os.WriteFile(
						storageFile,
						data,
						0o600,
					)
				}

				storageMu.Unlock()

				if err != nil {
					panic(
						runtime.ToValue(
							fmt.Sprintf(
								"storage.remove failed: %v",
								err,
							),
						),
					)
				}

				return goja.Undefined()
			},
		)

		runtime.Set(
			"storage",
			storageObject,
		)

		httpObject := runtime.NewObject()

		httpObject.Set(
			"get",
			func(call goja.FunctionCall) goja.Value {
				return executeHTTPFromCall(
					runtime,
					&manifest,
					httpClient,
					"GET",
					call,
				)
			},
		)

		httpObject.Set(
			"post",
			func(call goja.FunctionCall) goja.Value {
				return executeHTTPFromCall(
					runtime,
					&manifest,
					httpClient,
					"POST",
					call,
				)
			},
		)

		httpObject.Set(
			"put",
			func(call goja.FunctionCall) goja.Value {
				return executeHTTPFromCall(
					runtime,
					&manifest,
					httpClient,
					"PUT",
					call,
				)
			},
		)

		httpObject.Set(
			"delete",
			func(call goja.FunctionCall) goja.Value {
				return executeHTTPFromCall(
					runtime,
					&manifest,
					httpClient,
					"DELETE",
					call,
				)
			},
		)

		httpObject.Set(
			"patch",
			func(call goja.FunctionCall) goja.Value {
				return executeHTTPFromCall(
					runtime,
					&manifest,
					httpClient,
					"PATCH",
					call,
				)
			},
		)

		httpObject.Set(
			"request",
			func(call goja.FunctionCall) goja.Value {
				return executeGenericHTTPFromCall(
					runtime,
					&manifest,
					httpClient,
					call,
				)
			},
		)

		httpObject.Set(
			"clearCookies",
			func(call goja.FunctionCall) goja.Value {
				newJar, err := newCookieJar()

				if err != nil {
					panic(
						runtime.ToValue(
							fmt.Sprintf(
								"unable to clear cookies: %v",
								err,
							),
						),
					)
				}

				httpClient.Jar = newJar

				return goja.Undefined()
			},
		)

		runtime.Set(
			"http",
			httpObject,
		)

		/*
		 * ---------------------------------------------------------
		 * Generic utility API
		 * ---------------------------------------------------------
		 */

		utilsObject := runtime.NewObject()

		utilsObject.Set(

			"randomUserAgent",

			func(call goja.FunctionCall) goja.Value {

				userAgents := []string{

					"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",

					"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",

					"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",

					"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
				}

				index, err := rand.Int(

					rand.Reader,

					big.NewInt(int64(len(userAgents))),
				)

				if err != nil {

					return runtime.ToValue(userAgents[0])

				}

				return runtime.ToValue(

					userAgents[index.Int64()],
				)

			},
		)

		utilsObject.Set(
			"parseJSON",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					panic("utils.parseJSON requires a string")
				}

				var value interface{}

				if err := json.Unmarshal(
					[]byte(call.Argument(0).String()),
					&value,
				); err != nil {
					panic(
						runtime.ToValue(
							fmt.Sprintf(
								"invalid JSON: %v",
								err,
							),
						),
					)
				}

				return runtime.ToValue(value)
			},
		)

		utilsObject.Set(
			"stringifyJSON",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					return runtime.ToValue("null")
				}

				data, err := json.Marshal(
					call.Argument(0).Export(),
				)

				if err != nil {
					panic(
						runtime.ToValue(
							fmt.Sprintf(
								"unable to stringify JSON: %v",
								err,
							),
						),
					)
				}

				return runtime.ToValue(string(data))
			},
		)

		utilsObject.Set(
			"base64Encode",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					return runtime.ToValue("")
				}

				return runtime.ToValue(
					base64.StdEncoding.EncodeToString(
						[]byte(call.Argument(0).String()),
					),
				)
			},
		)

		utilsObject.Set(
			"base64Decode",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					return runtime.ToValue("")
				}

				decoded, err :=
					base64.StdEncoding.DecodeString(
						call.Argument(0).String(),
					)

				if err != nil {
					panic(
						runtime.ToValue(
							fmt.Sprintf(
								"invalid base64: %v",
								err,
							),
						),
					)
				}

				return runtime.ToValue(string(decoded))
			},
		)

		utilsObject.Set(
			"md5",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					return runtime.ToValue("")
				}

				digest := md5.Sum(
					[]byte(call.Argument(0).String()),
				)

				return runtime.ToValue(
					fmt.Sprintf("%x", digest),
				)
			},
		)

		utilsObject.Set(
			"sha256",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					return runtime.ToValue("")
				}

				digest := sha256.Sum256(
					[]byte(call.Argument(0).String()),
				)

				return runtime.ToValue(
					fmt.Sprintf("%x", digest),
				)
			},
		)

		utilsObject.Set(
			"hmacSHA256",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) < 2 {
					panic(
						"utils.hmacSHA256 requires message and secret",
					)
				}

				mac := hmac.New(
					sha256.New,
					[]byte(call.Argument(1).String()),
				)

				_, _ = mac.Write(
					[]byte(call.Argument(0).String()),
				)

				return runtime.ToValue(
					fmt.Sprintf("%x", mac.Sum(nil)),
				)
			},
		)

		utilsObject.Set(
			"hmacSHA256Base64",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) < 2 {
					panic(
						"utils.hmacSHA256Base64 requires message and secret",
					)
				}

				mac := hmac.New(
					sha256.New,
					[]byte(call.Argument(1).String()),
				)

				_, _ = mac.Write(
					[]byte(call.Argument(0).String()),
				)

				return runtime.ToValue(
					base64.StdEncoding.EncodeToString(
						mac.Sum(nil),
					),
				)
			},
		)

		utilsObject.Set(
			"hmacSHA1",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) < 2 {
					panic(
						"utils.hmacSHA1 requires message and secret",
					)
				}

				message := exportByteArray(
					runtime,
					call.Argument(1),
				)

				secret := exportByteArray(
					runtime,
					call.Argument(0),
				)

				mac := hmac.New(
					sha1.New,
					secret,
				)

				_, _ = mac.Write(message)

				digest := mac.Sum(nil)

				result := make([]interface{}, len(digest))

				for index, value := range digest {
					result[index] = int(value)
				}

				return runtime.ToValue(result)
			},
		)

		runtime.Set(
			"utils",
			utilsObject,
		)

		/*
		 * ---------------------------------------------------------
		 * Extension registration API
		 * ---------------------------------------------------------
		 */

		runtime.Set(
			"registerExtension",
			func(call goja.FunctionCall) goja.Value {

				if len(call.Arguments) == 0 {
					panic(
						"registerExtension requires an object",
					)
				}

				registeredProvider =
					call.Argument(0).ToObject(runtime)

				return goja.Undefined()
			},
		)

		/*
		 * ---------------------------------------------------------
		 * Execute extension JavaScript
		 * ---------------------------------------------------------
		 */

		_, err = runtime.RunScript(
			manifest.Name,
			string(scriptBytes),
		)
	})

	if err != nil {
		return "", fmt.Errorf(
			"extension JavaScript failed: %w",
			err,
		)
	}

	if registeredProvider == nil {
		return "", fmt.Errorf(
			"extension did not call registerExtension()",
		)
	}

	eventLoop.Start()

	loaded := &loadedExtension{
		ID:         manifest.Name,
		Manifest:   manifest,
		Runtime:    runtime,
		Provider:   registeredProvider,
		Client:     httpClient,
		EventLoop:  eventLoop,
		StorageDir: storageDir,
	}

	extensionsMu.Lock()

	extensions[manifest.Name] = loaded

	extensionsMu.Unlock()

	return manifestJSON(manifest)
}

/*
 * -------------------------------------------------------------
 * HTTP helpers
 * -------------------------------------------------------------
 */

func executeHTTPFromCall(
	runtime *goja.Runtime,
	manifest *extensionManifest,
	client *http.Client,
	method string,
	call goja.FunctionCall,
) goja.Value {

	if len(call.Arguments) == 0 {
		panic(
			runtime.ToValue(
				"http request requires a URL",
			),
		)
	}

	requestURL :=
		call.Argument(0).String()

	var headers map[string]string

	if len(call.Arguments) >= 2 &&
		call.Argument(1) != nil &&
		call.Argument(1) != goja.Undefined() {

		headers = exportHeaders(
			runtime,
			call.Argument(1),
		)
	}

	var body interface{}

	if method != "GET" &&
		method != "DELETE" &&
		len(call.Arguments) >= 2 {

		/*
		 * For POST/PUT/PATCH:
		 *
		 *   http.post(url, body, headers)
		 *
		 * The second argument is the body and the
		 * third argument is the headers.
		 */
		bodyValue :=
			call.Argument(1)

		if len(call.Arguments) >= 3 {
			headers =
				exportHeaders(
					runtime,
					call.Argument(2),
				)
		}

		body =
			exportRequestBody(
				runtime,
				bodyValue,
			)
	}

	response, err :=
		executeHTTP(
			manifest,
			client,
			method,
			requestURL,
			headers,
			body,
		)

	if err != nil {
		panic(
			runtime.ToValue(
				err.Error(),
			),
		)
	}

	return runtime.ToValue(response)
}

func executeGenericHTTPFromCall(
	runtime *goja.Runtime,
	manifest *extensionManifest,
	client *http.Client,
	call goja.FunctionCall,
) goja.Value {

	if len(call.Arguments) < 2 {
		panic(
			runtime.ToValue(
				"http.request requires URL and options",
			),
		)
	}

	requestURL :=
		call.Argument(0).String()

	options :=
		call.Argument(1).ToObject(runtime)

	methodValue :=
		options.Get("method")

	method := "GET"

	if methodValue != nil &&
		methodValue != goja.Undefined() {

		method =
			strings.ToUpper(
				methodValue.String(),
			)
	}

	headers := map[string]string{}

	headersValue :=
		options.Get("headers")

	if headersValue != nil &&
		headersValue != goja.Undefined() {

		headers =
			exportHeaders(
				runtime,
				headersValue,
			)
	}

	var body interface{}

	bodyValue :=
		options.Get("body")

	if bodyValue != nil &&
		bodyValue != goja.Undefined() {

		body =
			exportRequestBody(
				runtime,
				bodyValue,
			)
	}

	response, err :=
		executeHTTP(
			manifest,
			client,
			method,
			requestURL,
			headers,
			body,
		)

	if err != nil {
		panic(
			runtime.ToValue(
				err.Error(),
			),
		)
	}

	return runtime.ToValue(response)
}

func executeHTTP(
	manifest *extensionManifest,
	client *http.Client,
	method string,
	requestURL string,
	headers map[string]string,
	body interface{},
) (map[string]interface{}, error) {

	if !networkPermissionAllowed(
		manifest.Permissions.Network,
		requestURL,
	) {
		return nil, fmt.Errorf(
			"network access denied for %s",
			requestURL,
		)
	}

	var bodyReader io.Reader

	if body != nil {

		switch value := body.(type) {

		case string:

			bodyReader =
				strings.NewReader(value)

		case []byte:

			bodyReader =
				bytes.NewReader(value)

		default:

			data, err :=
				json.Marshal(value)

			if err != nil {
				return nil, fmt.Errorf(
					"unable to encode request body: %w",
					err,
				)
			}

			bodyReader =
				bytes.NewReader(data)

			if !hasHeader(
				headers,
				"Content-Type",
			) {
				if headers == nil {
					headers = make(map[string]string)
				}
				headers["Content-Type"] =
					"application/json"
			}
		}
	}

	request, err :=
		http.NewRequest(
			method,
			requestURL,
			bodyReader,
		)

	if err != nil {
		return nil, fmt.Errorf(
			"unable to create HTTP request: %w",
			err,
		)
	}

	if !hasHeader(
		headers,
		"User-Agent",
	) {
		request.Header.Set(
			"User-Agent",
			"MusiFlac/1.0",
		)
	}

	for key, value := range headers {
		request.Header.Set(
			key,
			value,
		)
	}

	response, err :=
		client.Do(request)

	if err != nil {
		return nil, fmt.Errorf(
			"HTTP request failed: %w",
			err,
		)
	}

	defer response.Body.Close()

	responseBody, err :=
		io.ReadAll(
			response.Body,
		)

	if err != nil {
		return nil, fmt.Errorf(
			"unable to read HTTP response: %w",
			err,
		)
	}

	responseHeaders :=
		make(map[string]interface{})

	for key, values := range response.Header {

		if len(values) == 1 {

			responseHeaders[key] =
				values[0]

		} else {

			copied :=
				make([]string, len(values))

			copy(
				copied,
				values,
			)

			responseHeaders[key] =
				copied
		}
	}

	return map[string]interface{}{
		"statusCode": response.StatusCode,
		"status":     response.StatusCode,
		"ok": response.StatusCode >= 200 &&
			response.StatusCode < 300,
		"body":    string(responseBody),
		"headers": responseHeaders,
	}, nil
}

func exportByteArray(
	runtime *goja.Runtime,
	value goja.Value,
) []byte {

	if value == nil ||
		value == goja.Undefined() ||
		value == goja.Null() {
		return nil
	}

	object := value.ToObject(runtime)

	lengthValue := object.Get("length")

	if lengthValue != nil &&
		lengthValue != goja.Undefined() {

		length := int(lengthValue.ToInteger())

		if length >= 0 {
			result := make([]byte, length)

			for index := 0; index < length; index++ {
				item := object.Get(
					fmt.Sprintf("%d", index),
				)

				if item == nil ||
					item == goja.Undefined() {
					continue
				}

				number := int(item.ToInteger())

				result[index] = byte(number & 0xff)
			}

			return result
		}
	}

	return []byte(value.String())
}

func exportHeaders(
	runtime *goja.Runtime,
	value goja.Value,
) map[string]string {

	result := make(map[string]string)

	object :=
		value.ToObject(runtime)

	if object == nil {
		return result
	}

	for _, key := range object.Keys() {

		headerValue :=
			object.Get(key)

		if headerValue == nil ||
			headerValue == goja.Undefined() {
			continue
		}

		result[key] =
			headerValue.String()
	}

	return result
}

func exportRequestBody(
	runtime *goja.Runtime,
	value goja.Value,
) interface{} {

	if value == nil ||
		value == goja.Undefined() {

		return nil
	}

	if stringValue, ok :=
		value.Export().(string); ok {

		return stringValue
	}

	exported :=
		value.Export()

	/*
	 * Keep primitive values as-is.
	 */
	switch exported.(type) {

	case bool,
		float64,
		int,
		int64,
		nil:

		return exported
	}

	/*
	 * Objects/arrays are encoded later by executeHTTP.
	 */
	_ = runtime

	return exported
}

func hasHeader(
	headers map[string]string,
	target string,
) bool {

	for key := range headers {

		if strings.EqualFold(
			key,
			target,
		) {
			return true
		}
	}

	return false
}

/*
 * -------------------------------------------------------------
 * Network permission matching
 * -------------------------------------------------------------
 */

func networkPermissionAllowed(
	permissions []string,
	requestURL string,
) bool {

	parsed, err :=
		url.Parse(requestURL)

	if err != nil {
		return false
	}

	hostname :=
		strings.ToLower(
			parsed.Hostname(),
		)

	if hostname == "" {
		return false
	}

	for _, permission := range permissions {

		permission =
			strings.ToLower(
				strings.TrimSpace(
					permission,
				),
			)

		if permission == "" {
			continue
		}

		/*
		 * Exact hostname.
		 */
		if permission == hostname {
			return true
		}

		/*
		 * Wildcard:
		 *
		 * *.spotify.com
		 *
		 * Matches:
		 * api.spotify.com
		 * open.spotify.com
		 *
		 * It does not match spotify.com itself.
		 */
		if strings.HasPrefix(
			permission,
			"*.",
		) {

			suffix :=
				strings.TrimPrefix(
					permission,
					"*.",
				)

			if strings.HasSuffix(
				hostname,
				"."+suffix,
			) {
				return true
			}
		}
	}

	return false
}

/*
 * -------------------------------------------------------------
 * Cookie jar
 * -------------------------------------------------------------
 */

type simpleCookieJar struct {
	mu      sync.Mutex
	cookies map[string][]*http.Cookie
}

func newCookieJar() (
	http.CookieJar,
	error,
) {

	return &simpleCookieJar{
		cookies: make(
			map[string][]*http.Cookie,
		),
	}, nil
}

func (jar *simpleCookieJar) Cookies(
	requestURL *url.URL,
) []*http.Cookie {

	jar.mu.Lock()
	defer jar.mu.Unlock()

	host :=
		requestURL.Hostname()

	cookies :=
		jar.cookies[host]

	result :=
		make([]*http.Cookie, 0)

	for _, cookie := range cookies {

		if cookie == nil {
			continue
		}

		result =
			append(
				result,
				cookie,
			)
	}

	return result
}

func (jar *simpleCookieJar) SetCookies(
	requestURL *url.URL,
	cookies []*http.Cookie,
) {

	jar.mu.Lock()
	defer jar.mu.Unlock()

	host :=
		requestURL.Hostname()

	if len(cookies) == 0 {
		return
	}

	existing :=
		jar.cookies[host]

	for _, newCookie := range cookies {

		if newCookie == nil {
			continue
		}

		replaced := false

		for index, oldCookie := range existing {

			if oldCookie.Name ==
				newCookie.Name {

				existing[index] =
					newCookie

				replaced = true

				break
			}
		}

		if !replaced {
			existing =
				append(
					existing,
					newCookie,
				)
		}
	}

	jar.cookies[host] =
		existing
}

/*
 * -------------------------------------------------------------
 * General helpers
 * -------------------------------------------------------------
 */

func formatLogArguments(
	arguments []goja.Value,
) string {

	if len(arguments) == 0 {
		return ""
	}

	result := ""

	for index, argument := range arguments {

		if index > 0 {
			result += " "
		}

		if argument == nil ||
			argument == goja.Undefined() {

			result += "undefined"

			continue
		}

		result += argument.String()
	}

	return result
}

func GetExtensionMethod(
	extensionID string,
	method string,
) bool {

	extensionsMu.RLock()

	extension :=
		extensions[extensionID]

	extensionsMu.RUnlock()

	if extension == nil {
		return false
	}

	value :=
		extension.Provider.Get(method)

	return value != nil &&
		value != goja.Undefined()
}

func readExtensionPackage(
	packagePath string,
) ([]byte, []byte, error) {

	file, err :=
		os.Open(packagePath)

	if err != nil {
		return nil, nil, fmt.Errorf(
			"unable to open extension: %w",
			err,
		)
	}

	defer file.Close()

	info, err :=
		file.Stat()

	if err != nil {
		return nil, nil, fmt.Errorf(
			"unable to inspect extension: %w",
			err,
		)
	}

	if info.IsDir() {
		return nil, nil, fmt.Errorf(
			"extension path is a directory",
		)
	}

	archive, err :=
		zip.OpenReader(packagePath)

	if err != nil {
		return nil, nil, fmt.Errorf(
			"invalid .sflx package: %w",
			err,
		)
	}

	defer archive.Close()

	var manifestBytes []byte
	var scriptBytes []byte

	for _, entry := range archive.File {

		switch entry.Name {

		case "manifest.json":

			manifestBytes, err =
				readZipEntry(entry)

		case "index.js":

			scriptBytes, err =
				readZipEntry(entry)

		default:

			continue
		}

		if err != nil {
			return nil, nil, err
		}
	}

	if len(manifestBytes) == 0 {
		return nil, nil, fmt.Errorf(
			"extension is missing manifest.json",
		)
	}

	if len(scriptBytes) == 0 {
		return nil, nil, fmt.Errorf(
			"extension is missing index.js",
		)
	}

	return manifestBytes, scriptBytes, nil
}

func readZipEntry(
	entry *zip.File,
) ([]byte, error) {

	reader, err :=
		entry.Open()

	if err != nil {
		return nil, fmt.Errorf(
			"unable to open %s: %w",
			entry.Name,
			err,
		)
	}

	defer reader.Close()

	data, err :=
		io.ReadAll(reader)

	if err != nil {
		return nil, fmt.Errorf(
			"unable to read %s: %w",
			entry.Name,
			err,
		)
	}

	return data, nil
}

func manifestJSON(
	manifest extensionManifest,
) (string, error) {

	data, err :=
		json.Marshal(manifest)

	if err != nil {
		return "", fmt.Errorf(
			"unable to serialize manifest: %w",
			err,
		)
	}

	return string(data), nil
}

func LoadExtensionsFromDirectory(
	directoryPath string,
) (string, error) {

	entries, err :=
		os.ReadDir(directoryPath)

	if err != nil {
		return "", fmt.Errorf(
			"unable to read extension directory: %w",
			err,
		)
	}

	loaded :=
		make([]string, 0)

	for _, entry := range entries {

		if entry.IsDir() {
			continue
		}

		name :=
			entry.Name()

		if len(name) < 5 ||
			name[len(name)-5:] != ".sflx" {

			continue
		}

		packagePath :=
			fmt.Sprintf(
				"%s/%s",
				directoryPath,
				name,
			)

		manifestJSON, err :=
			LoadExtension(packagePath)

		if err != nil {
			return "", fmt.Errorf(
				"failed to load %s: %w",
				name,
				err,
			)
		}

		var manifest extensionManifest

		if err := json.Unmarshal(
			[]byte(manifestJSON),
			&manifest,
		); err != nil {

			return "", fmt.Errorf(
				"failed to read loaded extension metadata: %w",
				err,
			)
		}

		loaded =
			append(
				loaded,
				manifest.Name,
			)
	}

	result, err :=
		json.Marshal(loaded)

	if err != nil {
		return "", fmt.Errorf(
			"unable to serialize loaded extensions: %w",
			err,
		)
	}

	return string(result), nil
}
