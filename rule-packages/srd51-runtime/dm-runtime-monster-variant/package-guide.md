# DM Hell Hound attack profile example

Append this author package after the standard combination with `srd51_magic`
release `16` and `dm_runtime_house_rules` release `25`. The package replaces the
complete `action_profile` for `srd51_magic/monster.hell_hound/actions/bite`.

The variant Bite has a +7 attack modifier, a reach of 10 feet, 2d8 + 4 piercing
damage and 1d6 + 1 fire damage. These are DM-authored example values. The same
application mechanism consumes the standard and replacement profiles.

Build `dm-runtime-monster-variant.zip` with `build-bundles.py --output-dir` and
load it after the standard modules in the independent scenario runner.
