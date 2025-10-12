package com.smotrilka.DTOs;

import java.util.List;
import java.util.Map;

public class LinkFullInfo {
    public int id;
    public String name;
    public String link;
    public String description;
    public int rating;
    public String creatorLogin;
    public List<String> tags;
    public List<Map<String, Object>> comments;
    public List<Map<String, Object>> favorites;
    public List<Map<String, Object>> reactions;
    public List<Map<String, String>> metadata;
}