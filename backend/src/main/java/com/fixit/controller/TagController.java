package com.fixit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixit.dto.TagResponse;
import com.fixit.service.TagService;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    /** The fixed set of tags a question can have (tech, math, code, others), in that order. */
    @GetMapping
    public List<TagResponse> list() {
        return service.listAll().stream().map(TagResponse::from).toList();
    }
}
