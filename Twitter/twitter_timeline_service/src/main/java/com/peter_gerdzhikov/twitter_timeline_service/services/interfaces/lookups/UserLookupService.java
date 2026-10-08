package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.users.UserClientDTO;

public interface UserLookupService {

    /**
     * The users among the ids, by id; an id the gateway doesn't know is absent. No call is made for no ids.
     *
     * @param ids at most 100 ids, the gateway's limit; a page of a feed never has more
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the gateway can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the gateway doesn't answer within the read timeout
     */
    Map<UUID, UserClientDTO> findByIds(Collection<UUID> ids);
}
