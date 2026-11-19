package com.dps.teknokent.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.ActorRequest;
import com.dps.teknokent.dto.requests.IskurChannelPreferencesRequest;
import com.dps.teknokent.dto.requests.IskurMessageRequest;
import com.dps.teknokent.dto.responses.IskurChannelListResponse;
import com.dps.teknokent.dto.responses.IskurChannelPreferencesResponse;
import com.dps.teknokent.dto.responses.IskurMessagePageResponse;
import com.dps.teknokent.dto.responses.IskurMessageReceiptsResponse;
import com.dps.teknokent.dto.responses.IskurMessageResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurChannelService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The İŞKUR channels for interns and coordinators: Mesajlar, Genel Duyurular and the read receipts. */
@RestController
@RequestMapping("/api/iskur")
@RequiredArgsConstructor
@Tag(name = "İŞKUR channels")
public class IskurChannelController {

	private final IskurChannelService iskurChannelService;

	@GetMapping("/channels")
	public IskurChannelListResponse channels(@RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) UUID programId) {
		return iskurChannelService.list(CurrentActor.resolve(actorId), programId);
	}

	@GetMapping("/channels/{channelId}/messages")
	public IskurMessagePageResponse messages(@PathVariable UUID channelId,
			@RequestParam(required = false) UUID actorId, @RequestParam(required = false) Instant before) {
		return iskurChannelService.messages(CurrentActor.resolve(actorId), channelId, before);
	}

	@PostMapping("/channels/{channelId}/messages")
	public IskurMessageResponse post(@PathVariable UUID channelId, @Valid @RequestBody IskurMessageRequest request) {
		return iskurChannelService.post(CurrentActor.resolve(request.actorId()), channelId, request);
	}

	@PostMapping("/channels/{channelId}/read")
	public ResponseEntity<Void> markRead(@PathVariable UUID channelId, @Valid @RequestBody ActorRequest request) {
		iskurChannelService.markRead(CurrentActor.resolve(request.actorId()), channelId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/messages/{messageId}/acknowledge")
	public IskurMessageResponse acknowledge(@PathVariable UUID messageId, @Valid @RequestBody ActorRequest request) {
		return iskurChannelService.acknowledge(CurrentActor.resolve(request.actorId()), messageId);
	}

	@GetMapping("/messages/{messageId}/receipts")
	public IskurMessageReceiptsResponse receipts(@PathVariable UUID messageId,
			@RequestParam(required = false) UUID actorId) {
		return iskurChannelService.receipts(CurrentActor.resolve(actorId), messageId);
	}

	@GetMapping("/channel-preferences")
	public IskurChannelPreferencesResponse preferences(@RequestParam UUID programId,
			@RequestParam(required = false) UUID actorId) {
		return iskurChannelService.preferences(CurrentActor.resolve(actorId), programId);
	}

	@PutMapping("/channel-preferences")
	public IskurChannelPreferencesResponse updatePreferences(
			@Valid @RequestBody IskurChannelPreferencesRequest request) {
		return iskurChannelService.updatePreferences(CurrentActor.resolve(request.actorId()), request);
	}
}
