package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.storage;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.files.FileContentResponseDTO;

public interface FileService {

    /**
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.FileNotFoundException
     *         when no file has this id
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException
     *         when the object can't be read
     */
    FileContentResponseDTO open(UUID fileId);
}
