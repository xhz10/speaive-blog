package com.speaive.blog.interfaces.http.knowledge;

import com.speaive.blog.application.port.in.knowledge.HumorKnowledgeUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/studio/humor-materials")
public class StudioHumorKnowledgeController {
    private final HumorKnowledgeUseCase knowledge;
    private final HumorKnowledgeHttpMapper mapper;
    public StudioHumorKnowledgeController(HumorKnowledgeUseCase knowledge, HumorKnowledgeHttpMapper mapper) {
        this.knowledge = knowledge;
        this.mapper = mapper;
    }
    @GetMapping HumorKnowledgeResponses.MaterialList list() {
        return new HumorKnowledgeResponses.MaterialList(mapper.toResponses(knowledge.listMaterials()));
    }
    @GetMapping("/search") HumorKnowledgeResponses.MaterialList search(@RequestParam String q,
            @RequestParam(required=false) String mechanism) {
        return new HumorKnowledgeResponses.MaterialList(mapper.toResponses(knowledge.searchMaterials(q, mechanism)));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    HumorKnowledgeResponses.MaterialDetail create(@Valid @RequestBody HumorKnowledgeRequests.SaveMaterialRequest request) {
        return mapper.toResponse(knowledge.createMaterial(mapper.toCommand(request)));
    }
    @PutMapping("/{id}") HumorKnowledgeResponses.MaterialDetail update(@PathVariable String id,
            @Valid @RequestBody HumorKnowledgeRequests.SaveMaterialRequest request) {
        return mapper.toResponse(knowledge.updateMaterial(id, mapper.toCommand(request)));
    }
}
