package com.example.lolwatcher;

import java.util.List;

public record PatchNotesSummary(
        String title,
        String url,
        List<String> highlights
) {
}
