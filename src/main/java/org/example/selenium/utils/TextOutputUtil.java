package org.example.selenium.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class TextOutputUtil {

    public static void output(String content, String fileName) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(fileName, true), StandardCharsets.UTF_8))) {
            bw.write(content);
            bw.newLine();
        }
    }
}
