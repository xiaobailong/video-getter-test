package org.example.selenium.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class TextOutputUtil {

     public static void output(String content, String fileName) throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName, true));
        bw.write(content + "\n");
        bw.close();
    }
}
