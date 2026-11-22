package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;

record Schema2CharacterMaterial(ProductionSchema2Projection projection, InitialPlan initial) { }
