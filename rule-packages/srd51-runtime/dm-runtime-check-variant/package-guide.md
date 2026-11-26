# Finite DM check variant

The `dm_runtime_house_rules` version `22` extension is the fourth member of
the bundled check variant. The first three members are
`srd51_runtime=11`, `srd51_characters=11` and `srd51_magic=11`, in that order.

This variant enables `allow_alternate_skill_ability` on the check definition.
Athletics defaults to Wisdom. The DM can choose a different ability for one
skill check, such as Constitution (Athletics) for a long swim. The check uses
the chosen ability modifier and the character's proficiency in the selected skill.

The variant sets `passive_base_score` to 12 and
`passive_advantage_adjustment` to 3. A passive check adds the applicable
modifiers to 12 and uses +3 for advantage, −3 for disadvantage or zero when
both or neither apply.

The variant grants Gnomish in the custom artisan
background, uses three times the proficiency bonus for Dwarf Stonecunning,
and sets the DM's fixed bludgeoning damage to 3 HP. The check and
vital-effect consumers interpret the declared values. The other rules retain
the finite application scope.
