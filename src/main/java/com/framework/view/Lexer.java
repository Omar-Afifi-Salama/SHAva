package com.framework.view;

import java.util.Objects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.List;
import java.util.ArrayList;

final class Lexer {
    private final String sourceCode;
    private final int codeLength;
    private static final BinaryUnit MAX_FILE_SIZE_UNIT = BinaryUnit.MiB; // get unit size as enum
    private static final int MAX_FILE_SIZE_AMOUNT = 16; // get amount of above size
    private static final long MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_AMOUNT * MAX_FILE_SIZE_UNIT.bytes; // get total size

    Lexer(String sourceCode) {
        this.sourceCode = Objects.requireNonNull(sourceCode, "Source code cannot be null");
        this.codeLength = this.sourceCode.length();
    }

    static Lexer fromFile(String filename) throws IOException {
        Objects.requireNonNull(filename, "Filename cannot be null");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be blank");
        }
        return Lexer.fromFile(Path.of(filename));
    }

    static Lexer fromFile(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "File path cannot be null");
        Path absPath = filePath.toRealPath();

        BasicFileAttributes attrs = Files.readAttributes(absPath, BasicFileAttributes.class);
        if (!attrs.isRegularFile()) {
            throw new IOException(absPath + " is not a regular file");
        }

        long fileSize = attrs.size();
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new IOException("File " + absPath + " exceeds maximum size limit of " + MAX_FILE_SIZE_AMOUNT + " " + MAX_FILE_SIZE_UNIT.label);
        }

        return new Lexer(Files.readString(absPath));
    }
}
