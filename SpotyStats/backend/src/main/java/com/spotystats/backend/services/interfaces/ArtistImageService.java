package com.spotystats.backend.services.interfaces;

import java.util.List;
import java.util.Map;


public interface ArtistImageService {

    Map<String, String> imageUrlsFor(List<String> artistIds);
}
