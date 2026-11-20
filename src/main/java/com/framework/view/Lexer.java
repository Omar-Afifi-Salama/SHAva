package com.framework.view;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

public class Lexer {
    private final String filename;

    Lexer(String filename) {
        this.filename = filename;
    }

    Path filePath = Paths.get(filename).toRealPath();
}
