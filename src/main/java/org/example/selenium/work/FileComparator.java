package org.example.selenium.work;

import java.io.File;
import java.util.Comparator;

public class FileComparator implements Comparator<File> {

    @Override
    public int compare(File o1, File o2) {
        int i1 = Integer.parseInt(o1.getName().split("-")[1]);
        int i2 = Integer.parseInt(o2.getName().split("-")[1]);
        if (i1 < i2) {
            return -1;
        } else if (i1 > i2) {
            return 1;
        } else {
            return 0;
        }
    }
}
