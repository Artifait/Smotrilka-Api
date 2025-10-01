package com.smotrilka.DTOs;


public class ReactionRequest {
    private String login;
    private String password;
    private Long linkId;
    private Integer reaction; // -1, 0, or +1

    public ReactionRequest() {}

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Long getLinkId() { return linkId; }
    public void setLinkId(Long linkId) { this.linkId = linkId; }

    public Integer getReaction() { return reaction; }
    public void setReaction(Integer reaction) { this.reaction = reaction; }
}
