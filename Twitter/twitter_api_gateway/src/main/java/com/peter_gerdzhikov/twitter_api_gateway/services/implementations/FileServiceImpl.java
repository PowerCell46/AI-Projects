package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.files.FileContentResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.FileNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.FileService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ObjectStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final DbFileRepository dbFileRepository;

    private final ObjectStorageService objectStorageService;

    @Override
    public FileContentResponseDTO open(UUID fileId) {
        DbFile file = dbFileRepository
                .findById(fileId)
                .orElseThrow(FileNotFoundException::new);

        return FileContentResponseDTO.builder()
                .sizeBytes(file.getSizeBytes())
                .contentType(file.getContentType())
                .content(objectStorageService.get(file.getObjectKey()))
                .build();
    }
}
