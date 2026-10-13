package com.speaive.blog.application.port.in.knowledge;

import com.speaive.blog.application.command.knowledge.SaveHumorMaterialCommand;
import com.speaive.blog.application.result.knowledge.HumorMaterialResult;
import java.util.List;

public interface HumorKnowledgeUseCase {
    List<HumorMaterialResult> listMaterials();
    List<HumorMaterialResult> searchMaterials(String text, String mechanism);
    HumorMaterialResult createMaterial(SaveHumorMaterialCommand command);
    HumorMaterialResult updateMaterial(String id, SaveHumorMaterialCommand command);
}
