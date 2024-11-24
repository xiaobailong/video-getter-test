package org.example.selenium.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class UrlInfo {

    private String fileName;

    private String protocol;

    private List<String> urlPaths = new ArrayList<>();

    private Map<String, String> parameters = new HashMap<>();

}
