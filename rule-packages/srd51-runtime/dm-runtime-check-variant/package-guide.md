# Finite DM check variant

The `dm_runtime_house_rules` version `20` extension is the fourth member of
the bundled check variant. The first three members are
`srd51_runtime=10`, `srd51_characters=10` and `srd51_magic=10`, in that order.

This variant enables `allow_alternate_skill_ability` on the ordinary check.
Athletics defaults to Wisdom. The DM can choose a different ability for one
skill check, such as Constitution (Athletics) for a long swim. The check uses
the chosen ability modifier and the character's proficiency in the selected skill.

The variant grants Gnomish in the custom artisan
background, uses three times the proficiency bonus for Dwarf Stonecunning,
and sets the DM's fixed bludgeoning damage to 3 HP. The ordinary check and
vital-effect consumers interpret the declared values. The other rules retain
the finite application scope.
