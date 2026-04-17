package ai

import "testing"

func TestStripJSONFences(t *testing.T) {
	tests := map[string]string{
		`{"ok":true}`:                    `{"ok":true}`,
		"```json\n{\"ok\":true}\n```":    `{"ok":true}`,
		"```JSON\n{\"ok\":true}\n``` \n": `{"ok":true}`,
		"```\n{\"ok\":true}\n```":        `{"ok":true}`,
	}

	for in, want := range tests {
		if got := StripJSONFences(in); got != want {
			t.Fatalf("StripJSONFences(%q) = %q, want %q", in, got, want)
		}
	}
}
