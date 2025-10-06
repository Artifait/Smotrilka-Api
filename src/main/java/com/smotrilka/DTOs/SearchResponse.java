package com.smotrilka.DTOs;

import java.util.List;

public class SearchResponse {
    private Integer id;
    private String name;
    private String link;
    private Integer rating;
    private List<String> tags;

    public SearchResponse(Integer id, String name, String link, Integer rating, List<String> tags) {
        this.id = id;
        this.name = name;
        this.link = link;
        this.rating = rating;
        this.tags = tags;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getLink() { return link; }
    public Integer getRating() { return rating; }
    public List<String> getTags() { return tags; }
}
