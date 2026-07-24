package com.documentai.platform.service;

import com.documentai.platform.dto.response.AskResponse;

import java.util.UUID;

public interface AskService {

    AskResponse ask(UUID workspaceId, String question);
}
