package com.smotrilka.DTOs;

import java.util.List;

public class LinkRequest {
    private String login;
    private String password;
    private String name;
    private String link;
    private List<String> tags; // заменяем "type" на список тегов

    public LinkRequest() {}

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}