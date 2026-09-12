package gobackend

import (
	"archive/zip"
	"os"
	"testing"
)

func TestPromiseExtension(t *testing.T) {
	const packagePath = "/tmp/musiflac-promise-test.sflx"

	manifest := `{
		"name": "promise-test",
		"displayName": "Promise Test",
		"version": "1.0.0",
		"description": "Promise bridge test",
		"type": ["metadata_provider"],
		"permissions": {
			"network": []
		}
	}`

	script := `
		registerExtension({
			searchTracks: async function(query) {
				return {
					tracks: ["hello", query]
				};
			}
		});
	`

	file, err := os.Create(packagePath)
	if err != nil {
		t.Fatal(err)
	}

	archive := zip.NewWriter(file)

	manifestWriter, err := archive.Create("manifest.json")
	if err != nil {
		file.Close()
		t.Fatal(err)
	}

	if _, err := manifestWriter.Write([]byte(manifest)); err != nil {
		archive.Close()
		file.Close()
		t.Fatal(err)
	}

	scriptWriter, err := archive.Create("index.js")
	if err != nil {
		archive.Close()
		file.Close()
		t.Fatal(err)
	}

	if _, err := scriptWriter.Write([]byte(script)); err != nil {
		archive.Close()
		file.Close()
		t.Fatal(err)
	}

	if err := archive.Close(); err != nil {
		file.Close()
		t.Fatal(err)
	}

	if err := file.Close(); err != nil {
		t.Fatal(err)
	}

	defer os.Remove(packagePath)

	_, loadErr := LoadExtension(packagePath)
	if loadErr != nil {
		t.Fatal(loadErr)
	}

	extensionID := "promise-test"

	result, err := CallExtensionMethod(
		extensionID,
		"searchTracks",
		`["test"]`,
	)

	if err != nil {
		t.Fatal(err)
	}

	expected := `{"tracks":["hello","test"]}`

	if result != expected {
		t.Fatalf(
			"unexpected result:\n got: %s\nwant: %s",
			result,
			expected,
		)
	}
}
