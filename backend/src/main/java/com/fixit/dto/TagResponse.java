package com.fixit.dto;

import com.fixit.entity.Tag;

public record TagResponse(Long tagId, String name) {

    public static TagResponse from(Tag t) {
        return new TagResponse(t.getId(), t.getName());
    }
}
