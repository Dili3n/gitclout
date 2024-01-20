package fr.uge.gitclout.tags.api;

import fr.uge.gitclout.tags.api.data.Progress;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/sse")
public class SseController {

    private SseEmitter emitter;
    private Long lastId = 0L;

    @GetMapping("/subscribe")
    public Mono<SseEmitter> subscribe() {
        try {
            this.emitter = new SseEmitter(0L);
            this.emitter.send(SseEmitter.event()
                    .name("message")
                    .id("" + lastId++)
                    .data(new Progress("subscribe", 0, 0)));
            return Mono.just(this.emitter);
        } catch (IOException e) {
            this.emitter.completeWithError(e);
            throw new RuntimeException("Error: Subscribing to SSE", e);
        }
    }

    public void sendProgress(Progress progress) {
        try {
            if (this.emitter != null) {
                this.emitter.send(SseEmitter.event()
                        .name("message")
                        .id("" + ++lastId)
                        .data(progress));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error: Sending progress", e);
        }


    }
}
