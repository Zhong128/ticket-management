package org.example.ticketmanagement.service;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarService {
    String upload(Long userId, MultipartFile file);

    String clear(Long userId);
}
