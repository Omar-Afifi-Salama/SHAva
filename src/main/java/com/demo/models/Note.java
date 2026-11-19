package com.demo.models;

import com.framework.annotations.data.Id;
import com.framework.annotations.data.Table;
import com.framework.data.Model;

@Table("notes")
public class Note extends Model<Note> {

    @Id
    private int id;
    private int userId;
    private String title;
    private String body;
    private String createdAt;

    public Note() {}

    public Note(int userId, String title, String body, String createdAt) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
