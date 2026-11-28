# DM Wolf Bite saving throw example

Append this author package after the standard combination with `srd51_magic`
release `17` and `dm_runtime_house_rules` release `25`. It replaces the complete
`action_profile` for `srd51_magic/monster.wolf/actions/bite` with the same values
except for the on-hit Strength saving throw DC, which changes from 11 to 12.

Build `dm-runtime-wolf-variant.zip` with `build-bundles.py --output-dir` and load
it after the standard modules in the independent scenario runner.
