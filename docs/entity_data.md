# Minecraft Entity Data Fields

## Overview

- [Entity](#entity)
  - [Area Effect Cloud](#area-effect-cloud)
  - [Block Attached Entity](#block-attached-entity)
    - [Hanging Entity](#hanging-entity)
      - [Item Frame](#item-frame)
        - [Glow Item Frame](#glow-item-frame)
      - [Painting](#painting)
    - [Leash Fence Knot Entity](#leash-fence-knot-entity)
  - [Display](#display)
    - [Block Display](#block-display)
    - [Item Display](#item-display)
    - [Text Display](#text-display)
  - [End Crystal](#end-crystal)
  - [Ender Dragon Part](#ender-dragon-part)
  - [Evoker Fangs](#evoker-fangs)
  - [Experience Orb](#experience-orb)
  - [Eye Of Ender](#eye-of-ender)
  - [Falling Block Entity](#falling-block-entity)
  - [Interaction](#interaction)
  - [Item Entity](#item-entity)
  - [Lightning Bolt](#lightning-bolt)
  - [Living Entity](#living-entity)
    - [Armor Stand](#armor-stand)
    - [Avatar](#avatar)
      - [Mannequin](#mannequin)
      - [Player](#player)
    - [Mob](#mob)
      - [Ambient Creature](#ambient-creature)
        - [Bat](#bat)
      - [Ender Dragon](#ender-dragon)
      - [Ghast](#ghast)
      - [Pathfinder Mob](#pathfinder-mob)
        - [Abstract Golem](#abstract-golem)
          - [Copper Golem](#copper-golem)
          - [Iron Golem](#iron-golem)
          - [Shulker](#shulker)
          - [Snow Golem](#snow-golem)
        - [Ageable Mob](#ageable-mob)
          - [Abstract Cube Mob](#abstract-cube-mob)
            - [Magma Cube](#magma-cube)
            - [Slime](#slime)
            - [Sulfur Cube](#sulfur-cube)
          - [Abstract Villager](#abstract-villager)
            - [Villager](#villager)
            - [Wandering Trader](#wandering-trader)
          - [Ageable Water Creature](#ageable-water-creature)
            - [Dolphin](#dolphin)
            - [Squid](#squid)
              - [Glow Squid](#glow-squid)
          - [Animal](#animal)
            - [Abstract Cow](#abstract-cow)
              - [Cow](#cow)
              - [Mushroom Cow](#mushroom-cow)
            - [Abstract Horse](#abstract-horse)
              - [Abstract Chested Horse](#abstract-chested-horse)
                - [Donkey](#donkey)
                - [Llama](#llama)
                  - [Trader Llama](#trader-llama)
                - [Mule](#mule)
              - [Camel](#camel)
                - [Camel Husk](#camel-husk)
              - [Horse](#horse)
              - [Skeleton Horse](#skeleton-horse)
              - [Zombie Horse](#zombie-horse)
            - [Armadillo](#armadillo)
            - [Axolotl](#axolotl)
            - [Bee](#bee)
            - [Chicken](#chicken)
            - [Fox](#fox)
            - [Frog](#frog)
            - [Goat](#goat)
            - [Happy Ghast](#happy-ghast)
            - [Hoglin](#hoglin)
            - [Ocelot](#ocelot)
            - [Panda](#panda)
            - [Pig](#pig)
            - [Polar Bear](#polar-bear)
            - [Rabbit](#rabbit)
            - [Sheep](#sheep)
            - [Sniffer](#sniffer)
            - [Strider](#strider)
            - [Tamable Animal](#tamable-animal)
              - [Abstract Nautilus](#abstract-nautilus)
                - [Nautilus](#nautilus)
                - [Zombie Nautilus](#zombie-nautilus)
              - [Cat](#cat)
              - [Shoulder Riding Entity](#shoulder-riding-entity)
                - [Parrot](#parrot)
              - [Wolf](#wolf)
            - [Turtle](#turtle)
        - [Allay](#allay)
        - [Monster](#monster)
          - [Abstract Piglin](#abstract-piglin)
            - [Piglin](#piglin)
            - [Piglin Brute](#piglin-brute)
          - [Abstract Skeleton](#abstract-skeleton)
            - [Bogged](#bogged)
            - [Parched](#parched)
            - [Skeleton](#skeleton)
            - [Stray](#stray)
            - [Wither Skeleton](#wither-skeleton)
          - [Blaze](#blaze)
          - [Breeze](#breeze)
          - [Creaking](#creaking)
          - [Creeper](#creeper)
          - [Ender Man](#ender-man)
          - [Endermite](#endermite)
          - [Giant](#giant)
          - [Guardian](#guardian)
            - [Elder Guardian](#elder-guardian)
          - [Patrolling Monster](#patrolling-monster)
            - [Raider](#raider)
              - [Abstract Illager](#abstract-illager)
                - [Pillager](#pillager)
                - [Spellcaster Illager](#spellcaster-illager)
                  - [Evoker](#evoker)
                  - [Illusioner](#illusioner)
                - [Vindicator](#vindicator)
              - [Ravager](#ravager)
              - [Witch](#witch)
          - [Silverfish](#silverfish)
          - [Spider](#spider)
            - [Cave Spider](#cave-spider)
          - [Vex](#vex)
          - [Warden](#warden)
          - [Wither Boss](#wither-boss)
          - [Zoglin](#zoglin)
          - [Zombie](#zombie)
            - [Drowned](#drowned)
            - [Husk](#husk)
            - [Zombie Villager](#zombie-villager)
            - [Zombified Piglin](#zombified-piglin)
        - [Water Animal](#water-animal)
          - [Abstract Fish](#abstract-fish)
            - [Abstract Schooling Fish](#abstract-schooling-fish)
              - [Cod](#cod)
              - [Salmon](#salmon)
              - [Tropical Fish](#tropical-fish)
            - [Pufferfish](#pufferfish)
            - [Tadpole](#tadpole)
      - [Phantom](#phantom)
  - [Marker](#marker)
  - [Ominous Item Spawner](#ominous-item-spawner)
  - [Primed Tnt](#primed-tnt)
  - [Projectile](#projectile)
    - [Abstract Arrow](#abstract-arrow)
      - [Arrow](#arrow)
      - [Spectral Arrow](#spectral-arrow)
      - [Thrown Trident](#thrown-trident)
    - [Abstract Hurting Projectile](#abstract-hurting-projectile)
      - [Abstract Wind Charge](#abstract-wind-charge)
        - [Breeze Wind Charge](#breeze-wind-charge)
        - [Wind Charge](#wind-charge)
      - [Dragon Fireball](#dragon-fireball)
      - [Fireball](#fireball)
        - [Large Fireball](#large-fireball)
        - [Small Fireball](#small-fireball)
      - [Wither Skull](#wither-skull)
    - [Firework Rocket Entity](#firework-rocket-entity)
    - [Fishing Hook](#fishing-hook)
    - [Llama Spit](#llama-spit)
    - [Shulker Bullet](#shulker-bullet)
    - [Throwable Projectile](#throwable-projectile)
      - [Throwable Item Projectile](#throwable-item-projectile)
        - [Abstract Thrown Potion](#abstract-thrown-potion)
          - [Thrown Lingering Potion](#thrown-lingering-potion)
          - [Thrown Splash Potion](#thrown-splash-potion)
        - [Snowball](#snowball)
        - [Thrown Egg](#thrown-egg)
        - [Thrown Enderpearl](#thrown-enderpearl)
        - [Thrown Experience Bottle](#thrown-experience-bottle)
  - [Vehicle Entity](#vehicle-entity)
    - [Abstract Boat](#abstract-boat)
      - [Abstract Chest Boat](#abstract-chest-boat)
        - [Chest Boat](#chest-boat)
        - [Chest Raft](#chest-raft)
      - [Boat](#boat)
      - [Raft](#raft)
    - [Abstract Minecart](#abstract-minecart)
      - [Abstract Minecart Container](#abstract-minecart-container)
        - [Minecart Chest](#minecart-chest)
        - [Minecart Hopper](#minecart-hopper)
      - [Minecart](#minecart)
      - [Minecart Command Block](#minecart-command-block)
      - [Minecart Furnace](#minecart-furnace)
      - [Minecart Spawner](#minecart-spawner)
      - [Minecart TNT](#minecart-tnt)


## Entity Details

### Entity
**Extends:** None

| Index | Data Type                 | Field Name          | Default                |
|-------|---------------------------|---------------------|------------------------|
| 0     | Byte                      | SHARED_FLAGS        | (byte)0                |
| 1     | Integer                   | AIR_SUPPLY          | this.getMaxAirSupply() |
| 2     | Optional&lt;Component&gt; | CUSTOM_NAME         | Optional.empty()       |
| 3     | Boolean                   | CUSTOM_NAME_VISIBLE | false                  |
| 4     | Boolean                   | SILENT              | false                  |
| 5     | Boolean                   | NO_GRAVITY          | false                  |
| 6     | Pose                      | POSE                | Pose.STANDING          |
| 7     | Integer                   | TICKS_FROZEN        | 0                      |


### Area Effect Cloud
**Extends:** [Entity](#entity)

| Index | Data Type       | Field Name | Default          |
|-------|-----------------|------------|------------------|
| 8     | Float           | RADIUS     | 3.0F             |
| 9     | Boolean         | WAITING    | false            |
| 10    | ParticleOptions | PARTICLE   | DEFAULT_PARTICLE |


### Block Attached Entity
**Extends:** [Entity](#entity)

No data.


### Display
**Extends:** [Entity](#entity)

| Index | Data Type    | Field Name                                     | Default                                    |
|-------|--------------|------------------------------------------------|--------------------------------------------|
| 8     | Integer      | TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS | 0                                          |
| 9     | Integer      | TRANSFORMATION_INTERPOLATION_DURATION          | 0                                          |
| 10    | Integer      | POS_ROT_INTERPOLATION_DURATION                 | 0                                          |
| 11    | Vector3fc    | TRANSLATION                                    | new Vector3f()                             |
| 12    | Vector3fc    | SCALE                                          | new Vector3f(1.0F, 1.0F, 1.0F)             |
| 13    | Quaternionfc | LEFT_ROTATION                                  | new Quaternionf()                          |
| 14    | Quaternionfc | RIGHT_ROTATION                                 | new Quaternionf()                          |
| 15    | Byte         | BILLBOARD_RENDER_CONSTRAINTS                   | Display.BillboardConstraints.FIXED.getId() |
| 16    | Integer      | BRIGHTNESS_OVERRIDE                            | -1                                         |
| 17    | Float        | VIEW_RANGE                                     | 1.0F                                       |
| 18    | Float        | SHADOW_RADIUS                                  | 0.0F                                       |
| 19    | Float        | SHADOW_STRENGTH                                | 1.0F                                       |
| 20    | Float        | WIDTH                                          | 0.0F                                       |
| 21    | Float        | HEIGHT                                         | 0.0F                                       |
| 22    | Integer      | GLOW_COLOR_OVERRIDE                            | -1                                         |


### End Crystal
**Extends:** [Entity](#entity)

| Index | Data Type                | Field Name  | Default          |
|-------|--------------------------|-------------|------------------|
| 8     | Optional&lt;BlockPos&gt; | BEAM_TARGET | Optional.empty() |
| 9     | Boolean                  | SHOW_BOTTOM | true             |


### Ender Dragon Part
**Extends:** [Entity](#entity)

No data.


### Evoker Fangs
**Extends:** [Entity](#entity)

No data.


### Experience Orb
**Extends:** [Entity](#entity)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 8     | Integer   | VALUE      | 0       |


### Eye Of Ender
**Extends:** [Entity](#entity)

| Index | Data Type | Field Name | Default               |
|-------|-----------|------------|-----------------------|
| 8     | ItemStack | ITEM_STACK | this.getDefaultItem() |


### Falling Block Entity
**Extends:** [Entity](#entity)

| Index | Data Type | Field Name | Default       |
|-------|-----------|------------|---------------|
| 8     | BlockPos  | START_POS  | BlockPos.ZERO |


### Interaction
**Extends:** [Entity](#entity)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 8     | Float     | WIDTH      | 1.0F    |
| 9     | Float     | HEIGHT     | 1.0F    |
| 10    | Boolean   | RESPONSE   | false   |


### Item Entity
**Extends:** [Entity](#entity)

| Index | Data Type | Field Name | Default         |
|-------|-----------|------------|-----------------|
| 8     | ItemStack | ITEM       | ItemStack.EMPTY |


### Lightning Bolt
**Extends:** [Entity](#entity)

No data.


### Living Entity
**Extends:** [Entity](#entity)

| Index | Data Type                   | Field Name          | Default          |
|-------|-----------------------------|---------------------|------------------|
| 8     | Byte                        | LIVING_ENTITY_FLAGS | (byte)0          |
| 9     | Float                       | HEALTH              | 1.0F             |
| 10    | List&lt;ParticleOptions&gt; | EFFECT_PARTICLES    | List.of()        |
| 11    | Boolean                     | EFFECT_AMBIENCE     | false            |
| 12    | Integer                     | ARROW_COUNT         | 0                |
| 13    | Integer                     | STINGER_COUNT       | 0                |
| 14    | Optional&lt;BlockPos&gt;    | SLEEPING_POS        | Optional.empty() |


### Marker
**Extends:** [Entity](#entity)

No data.


### Ominous Item Spawner
**Extends:** [Entity](#entity)

| Index | Data Type | Field Name | Default         |
|-------|-----------|------------|-----------------|
| 8     | ItemStack | ITEM       | ItemStack.EMPTY |


### Primed Tnt
**Extends:** [Entity](#entity)

| Index | Data Type  | Field Name  | Default             |
|-------|------------|-------------|---------------------|
| 8     | Integer    | FUSE        | 80                  |
| 9     | BlockState | BLOCK_STATE | DEFAULT_BLOCK_STATE |


### Projectile
**Extends:** [Entity](#entity)

No data.


### Vehicle Entity
**Extends:** [Entity](#entity)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 8     | Integer   | HURT       | 0       |
| 9     | Integer   | HURTDIR    | 1       |
| 10    | Float     | DAMAGE     | 0.0F    |


### Abstract Arrow
**Extends:** [Projectile](#projectile)

| Index | Data Type | Field Name   | Default |
|-------|-----------|--------------|---------|
| 8     | Byte      | ID_FLAGS     | (byte)0 |
| 9     | Byte      | PIERCE_LEVEL | (byte)0 |
| 10    | Boolean   | IN_GROUND    | false   |


### Abstract Boat
**Extends:** [Vehicle Entity](#vehicle-entity)

| Index | Data Type | Field Name   | Default |
|-------|-----------|--------------|---------|
| 11    | Boolean   | PADDLE_LEFT  | false   |
| 12    | Boolean   | PADDLE_RIGHT | false   |
| 13    | Integer   | BUBBLE_TIME  | 0       |


### Abstract Hurting Projectile
**Extends:** [Projectile](#projectile)

No data.


### Abstract Minecart
**Extends:** [Vehicle Entity](#vehicle-entity)

| Index | Data Type                  | Field Name           | Default                        |
|-------|----------------------------|----------------------|--------------------------------|
| 11    | Optional&lt;BlockState&gt; | CUSTOM_DISPLAY_BLOCK | Optional.empty()               |
| 12    | Integer                    | DISPLAY_OFFSET       | this.getDefaultDisplayOffset() |


### Armor Stand
**Extends:** [Living Entity](#living-entity)

| Index | Data Type | Field Name     | Default                |
|-------|-----------|----------------|------------------------|
| 15    | Byte      | CLIENT_FLAGS   | (byte)0                |
| 16    | Rotations | HEAD_POSE      | DEFAULT_HEAD_POSE      |
| 17    | Rotations | BODY_POSE      | DEFAULT_BODY_POSE      |
| 18    | Rotations | LEFT_ARM_POSE  | DEFAULT_LEFT_ARM_POSE  |
| 19    | Rotations | RIGHT_ARM_POSE | DEFAULT_RIGHT_ARM_POSE |
| 20    | Rotations | LEFT_LEG_POSE  | DEFAULT_LEFT_LEG_POSE  |
| 21    | Rotations | RIGHT_LEG_POSE | DEFAULT_RIGHT_LEG_POSE |


### Avatar
**Extends:** [Living Entity](#living-entity)

| Index | Data Type   | Field Name                | Default           |
|-------|-------------|---------------------------|-------------------|
| 15    | HumanoidArm | PLAYER_MAIN_HAND          | DEFAULT_MAIN_HAND |
| 16    | Byte        | PLAYER_MODE_CUSTOMISATION | (byte)0           |


### Block Display
**Extends:** [Display](#display)

| Index | Data Type  | Field Name  | Default                        |
|-------|------------|-------------|--------------------------------|
| 23    | BlockState | BLOCK_STATE | Blocks.AIR.defaultBlockState() |


### Firework Rocket Entity
**Extends:** [Projectile](#projectile)

| Index | Data Type   | Field Name         | Default             |
|-------|-------------|--------------------|---------------------|
| 8     | ItemStack   | FIREWORKS_ITEM     | getDefaultItem()    |
| 9     | OptionalInt | ATTACHED_TO_TARGET | OptionalInt.empty() |
| 10    | Boolean     | SHOT_AT_ANGLE      | false               |


### Fishing Hook
**Extends:** [Projectile](#projectile)

| Index | Data Type | Field Name    | Default |
|-------|-----------|---------------|---------|
| 8     | Integer   | HOOKED_ENTITY | 0       |
| 9     | Boolean   | BITING        | false   |


### Hanging Entity
**Extends:** [Block Attached Entity](#block-attached-entity)

| Index | Data Type | Field Name | Default           |
|-------|-----------|------------|-------------------|
| 8     | Direction | DIRECTION  | DEFAULT_DIRECTION |


### Item Display
**Extends:** [Display](#display)

| Index | Data Type | Field Name   | Default                         |
|-------|-----------|--------------|---------------------------------|
| 23    | ItemStack | ITEM_STACK   | ItemStack.EMPTY                 |
| 24    | Byte      | ITEM_DISPLAY | ItemDisplayContext.NONE.getId() |


### Leash Fence Knot Entity
**Extends:** [Block Attached Entity](#block-attached-entity)

No data.


### Llama Spit
**Extends:** [Projectile](#projectile)

No data.


### Mob
**Extends:** [Living Entity](#living-entity)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 15    | Byte      | MOB_FLAGS  | (byte)0 |


### Shulker Bullet
**Extends:** [Projectile](#projectile)

No data.


### Text Display
**Extends:** [Display](#display)

| Index | Data Type | Field Name       | Default           |
|-------|-----------|------------------|-------------------|
| 23    | Component | TEXT             | Component.empty() |
| 24    | Integer   | LINE_WIDTH       | 200               |
| 25    | Integer   | BACKGROUND_COLOR | 1073741824        |
| 26    | Byte      | TEXT_OPACITY     | (byte)-1          |
| 27    | Byte      | STYLE_FLAGS      | (byte)0           |


### Throwable Projectile
**Extends:** [Projectile](#projectile)

No data.


### Abstract Chest Boat
**Extends:** [Abstract Boat](#abstract-boat)

No data.


### Abstract Minecart Container
**Extends:** [Abstract Minecart](#abstract-minecart)

No data.


### Abstract Wind Charge
**Extends:** [Abstract Hurting Projectile](#abstract-hurting-projectile)

No data.


### Ambient Creature
**Extends:** [Mob](#mob)

No data.


### Arrow
**Extends:** [Abstract Arrow](#abstract-arrow)

| Index | Data Type | Field Name      | Default |
|-------|-----------|-----------------|---------|
| 11    | Integer   | ID_EFFECT_COLOR | -1      |


### Boat
**Extends:** [Abstract Boat](#abstract-boat)

No data.


### Dragon Fireball
**Extends:** [Abstract Hurting Projectile](#abstract-hurting-projectile)

No data.


### Ender Dragon
**Extends:** [Mob](#mob)

| Index | Data Type | Field Name | Default                           |
|-------|-----------|------------|-----------------------------------|
| 16    | Integer   | PHASE      | EnderDragonPhase.HOVERING.getId() |


### Fireball
**Extends:** [Abstract Hurting Projectile](#abstract-hurting-projectile)

| Index | Data Type | Field Name | Default               |
|-------|-----------|------------|-----------------------|
| 8     | ItemStack | ITEM_STACK | this.getDefaultItem() |


### Ghast
**Extends:** [Mob](#mob)

| Index | Data Type | Field Name  | Default |
|-------|-----------|-------------|---------|
| 16    | Boolean   | IS_CHARGING | false   |


### Item Frame
**Extends:** [Hanging Entity](#hanging-entity)

| Index | Data Type | Field Name | Default         |
|-------|-----------|------------|-----------------|
| 9     | ItemStack | ITEM       | ItemStack.EMPTY |
| 10    | Integer   | ROTATION   | 0               |


### Mannequin
**Extends:** [Avatar](#avatar)

| Index | Data Type                 | Field Name  | Default                          |
|-------|---------------------------|-------------|----------------------------------|
| 17    | ResolvableProfile         | PROFILE     | DEFAULT_PROFILE                  |
| 18    | Boolean                   | IMMOVABLE   | false                            |
| 19    | Optional&lt;Component&gt; | DESCRIPTION | Optional.of(DEFAULT_DESCRIPTION) |


### Minecart
**Extends:** [Abstract Minecart](#abstract-minecart)

No data.


### Minecart Command Block
**Extends:** [Abstract Minecart](#abstract-minecart)

| Index | Data Type | Field Name   | Default                |
|-------|-----------|--------------|------------------------|
| 13    | String    | COMMAND_NAME | ""                     |
| 14    | Component | LAST_OUTPUT  | CommonComponents.EMPTY |


### Minecart Furnace
**Extends:** [Abstract Minecart](#abstract-minecart)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 13    | Boolean   | FUEL       | false   |


### Minecart Spawner
**Extends:** [Abstract Minecart](#abstract-minecart)

No data.


### Minecart TNT
**Extends:** [Abstract Minecart](#abstract-minecart)

No data.


### Painting
**Extends:** [Hanging Entity](#hanging-entity)

| Index | Data Type                     | Field Name       | Default                                                                 |
|-------|-------------------------------|------------------|-------------------------------------------------------------------------|
| 9     | Holder&lt;PaintingVariant&gt; | PAINTING_VARIANT | VariantUtils.getAny(this.registryAccess(), Registries.PAINTING_VARIANT) |


### Pathfinder Mob
**Extends:** [Mob](#mob)

No data.


### Phantom
**Extends:** [Mob](#mob)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Integer   | ID_SIZE    | 0       |


### Player
**Extends:** [Avatar](#avatar)

| Index | Data Type   | Field Name            | Default             |
|-------|-------------|-----------------------|---------------------|
| 17    | Float       | PLAYER_ABSORPTION     | 0.0F                |
| 18    | Integer     | SCORE                 | 0                   |
| 19    | OptionalInt | SHOULDER_PARROT_LEFT  | OptionalInt.empty() |
| 20    | OptionalInt | SHOULDER_PARROT_RIGHT | OptionalInt.empty() |


### Raft
**Extends:** [Abstract Boat](#abstract-boat)

No data.


### Spectral Arrow
**Extends:** [Abstract Arrow](#abstract-arrow)

No data.


### Throwable Item Projectile
**Extends:** [Throwable Projectile](#throwable-projectile)

| Index | Data Type | Field Name | Default                              |
|-------|-----------|------------|--------------------------------------|
| 8     | ItemStack | ITEM_STACK | new ItemStack(this.getDefaultItem()) |


### Thrown Trident
**Extends:** [Abstract Arrow](#abstract-arrow)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 11    | Byte      | ID_LOYALTY | (byte)0 |
| 12    | Boolean   | ID_FOIL    | false   |


### Wither Skull
**Extends:** [Abstract Hurting Projectile](#abstract-hurting-projectile)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 8     | Boolean   | DANGEROUS  | false   |


### Abstract Golem
**Extends:** [Pathfinder Mob](#pathfinder-mob)

No data.


### Abstract Thrown Potion
**Extends:** [Throwable Item Projectile](#throwable-item-projectile)

No data.


### Ageable Mob
**Extends:** [Pathfinder Mob](#pathfinder-mob)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Boolean   | BABY       | false   |
| 17    | Boolean   | AGE_LOCKED | false   |


### Allay
**Extends:** [Pathfinder Mob](#pathfinder-mob)

| Index | Data Type | Field Name    | Default |
|-------|-----------|---------------|---------|
| 16    | Boolean   | DANCING       | false   |
| 17    | Boolean   | CAN_DUPLICATE | true    |


### Bat
**Extends:** [Ambient Creature](#ambient-creature)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Byte      | FLAGS      | (byte)0 |


### Breeze Wind Charge
**Extends:** [Abstract Wind Charge](#abstract-wind-charge)

No data.


### Chest Boat
**Extends:** [Abstract Chest Boat](#abstract-chest-boat)

No data.


### Chest Raft
**Extends:** [Abstract Chest Boat](#abstract-chest-boat)

No data.


### Glow Item Frame
**Extends:** [Item Frame](#item-frame)

No data.


### Large Fireball
**Extends:** [Fireball](#fireball)

No data.


### Minecart Chest
**Extends:** [Abstract Minecart Container](#abstract-minecart-container)

No data.


### Minecart Hopper
**Extends:** [Abstract Minecart Container](#abstract-minecart-container)

No data.


### Monster
**Extends:** [Pathfinder Mob](#pathfinder-mob)

No data.


### Small Fireball
**Extends:** [Fireball](#fireball)

No data.


### Snowball
**Extends:** [Throwable Item Projectile](#throwable-item-projectile)

No data.


### Thrown Egg
**Extends:** [Throwable Item Projectile](#throwable-item-projectile)

No data.


### Thrown Enderpearl
**Extends:** [Throwable Item Projectile](#throwable-item-projectile)

No data.


### Thrown Experience Bottle
**Extends:** [Throwable Item Projectile](#throwable-item-projectile)

No data.


### Water Animal
**Extends:** [Pathfinder Mob](#pathfinder-mob)

No data.


### Wind Charge
**Extends:** [Abstract Wind Charge](#abstract-wind-charge)

No data.


### Abstract Cube Mob
**Extends:** [Ageable Mob](#ageable-mob)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 18    | Integer   | ID_SIZE    | 1       |


### Abstract Fish
**Extends:** [Water Animal](#water-animal)

| Index | Data Type | Field Name  | Default |
|-------|-----------|-------------|---------|
| 16    | Boolean   | FROM_BUCKET | false   |


### Abstract Piglin
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name              | Default |
|-------|-----------|-------------------------|---------|
| 16    | Boolean   | IMMUNE_TO_ZOMBIFICATION | false   |


### Abstract Skeleton
**Extends:** [Monster](#monster)

No data.


### Abstract Villager
**Extends:** [Ageable Mob](#ageable-mob)

| Index | Data Type | Field Name      | Default |
|-------|-----------|-----------------|---------|
| 18    | Integer   | UNHAPPY_COUNTER | 0       |


### Ageable Water Creature
**Extends:** [Ageable Mob](#ageable-mob)

No data.


### Animal
**Extends:** [Ageable Mob](#ageable-mob)

No data.


### Blaze
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Byte      | FLAGS      | (byte)0 |


### Breeze
**Extends:** [Monster](#monster)

No data.


### Copper Golem
**Extends:** [Abstract Golem](#abstract-golem)

| Index | Data Type                     | Field Name         | Default                                  |
|-------|-------------------------------|--------------------|------------------------------------------|
| 16    | WeatheringCopper.WeatherState | WEATHER_STATE      | WeatheringCopper.WeatherState.UNAFFECTED |
| 17    | CopperGolemState              | COPPER_GOLEM_STATE | CopperGolemState.IDLE                    |


### Creaking
**Extends:** [Monster](#monster)

| Index | Data Type                | Field Name      | Default          |
|-------|--------------------------|-----------------|------------------|
| 16    | Boolean                  | CAN_MOVE        | true             |
| 17    | Boolean                  | IS_ACTIVE       | false            |
| 18    | Boolean                  | IS_TEARING_DOWN | false            |
| 19    | Optional&lt;BlockPos&gt; | HOME_POS        | Optional.empty() |


### Creeper
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Integer   | SWELL_DIR  | -1      |
| 17    | Boolean   | IS_POWERED | false   |
| 18    | Boolean   | IS_IGNITED | false   |


### Ender Man
**Extends:** [Monster](#monster)

| Index | Data Type                  | Field Name  | Default          |
|-------|----------------------------|-------------|------------------|
| 16    | Optional&lt;BlockState&gt; | CARRY_STATE | Optional.empty() |
| 17    | Boolean                    | CREEPY      | false            |
| 18    | Boolean                    | STARED_AT   | false            |


### Endermite
**Extends:** [Monster](#monster)

No data.


### Giant
**Extends:** [Monster](#monster)

No data.


### Guardian
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name    | Default |
|-------|-----------|---------------|---------|
| 16    | Boolean   | MOVING        | false   |
| 17    | Integer   | ATTACK_TARGET | 0       |


### Iron Golem
**Extends:** [Abstract Golem](#abstract-golem)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Byte      | FLAGS      | (byte)0 |


### Patrolling Monster
**Extends:** [Monster](#monster)

No data.


### Shulker
**Extends:** [Abstract Golem](#abstract-golem)

| Index | Data Type | Field Name  | Default             |
|-------|-----------|-------------|---------------------|
| 16    | Direction | ATTACH_FACE | DEFAULT_ATTACH_FACE |
| 17    | Byte      | PEEK        | (byte)0             |
| 18    | Byte      | COLOR       | (byte)16            |


### Silverfish
**Extends:** [Monster](#monster)

No data.


### Snow Golem
**Extends:** [Abstract Golem](#abstract-golem)

| Index | Data Type | Field Name | Default  |
|-------|-----------|------------|----------|
| 16    | Byte      | PUMPKIN    | (byte)16 |


### Spider
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Byte      | FLAGS      | (byte)0 |


### Thrown Lingering Potion
**Extends:** [Abstract Thrown Potion](#abstract-thrown-potion)

No data.


### Thrown Splash Potion
**Extends:** [Abstract Thrown Potion](#abstract-thrown-potion)

No data.


### Vex
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Byte      | FLAGS      | (byte)0 |


### Warden
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name         | Default |
|-------|-----------|--------------------|---------|
| 16    | Integer   | CLIENT_ANGER_LEVEL | 0       |


### Wither Boss
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Integer   | TARGET_A   | 0       |
| 17    | Integer   | TARGET_B   | 0       |
| 18    | Integer   | TARGET_C   | 0       |
| 19    | Integer   | INV        | 0       |


### Zoglin
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Boolean   | BABY       | false   |


### Zombie
**Extends:** [Monster](#monster)

| Index | Data Type | Field Name         | Default |
|-------|-----------|--------------------|---------|
| 16    | Boolean   | BABY               | false   |
| 17    | Integer   | SPECIAL_TYPE       | 0       |
| 18    | Boolean   | DROWNED_CONVERSION | false   |


### Abstract Cow
**Extends:** [Animal](#animal)

No data.


### Abstract Horse
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 18    | Byte      | FLAGS      | (byte)0 |


### Abstract Schooling Fish
**Extends:** [Abstract Fish](#abstract-fish)

No data.


### Armadillo
**Extends:** [Animal](#animal)

| Index | Data Type                | Field Name      | Default                       |
|-------|--------------------------|-----------------|-------------------------------|
| 18    | Armadillo.ArmadilloState | ARMADILLO_STATE | Armadillo.ArmadilloState.IDLE |


### Axolotl
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name   | Default |
|-------|-----------|--------------|---------|
| 18    | Integer   | VARIANT      | 0       |
| 19    | Boolean   | PLAYING_DEAD | false   |
| 20    | Boolean   | FROM_BUCKET  | false   |


### Bee
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name     | Default |
|-------|-----------|----------------|---------|
| 18    | Byte      | FLAGS          | (byte)0 |
| 19    | Long      | ANGER_END_TIME | -1L     |


### Bogged
**Extends:** [Abstract Skeleton](#abstract-skeleton)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 16    | Boolean   | SHEARED    | false   |


### Cave Spider
**Extends:** [Spider](#spider)

No data.


### Chicken
**Extends:** [Animal](#animal)

| Index | Data Type                         | Field Name    | Default                                                                                               |
|-------|-----------------------------------|---------------|-------------------------------------------------------------------------------------------------------|
| 18    | Holder&lt;ChickenVariant&gt;      | VARIANT       | VariantUtils.getDefaultOrAny(this.registryAccess(), ChickenVariants.TEMPERATE)                        |
| 19    | Holder&lt;ChickenSoundVariant&gt; | SOUND_VARIANT | chickenSoundVariants.get(ChickenSoundVariants.CLASSIC).or(chickenSoundVariants::getAny).orElseThrow() |


### Dolphin
**Extends:** [Ageable Water Creature](#ageable-water-creature)

| Index | Data Type | Field Name      | Default |
|-------|-----------|-----------------|---------|
| 18    | Boolean   | GOT_FISH        | false   |
| 19    | Integer   | MOISTNESS_LEVEL | 2400    |


### Drowned
**Extends:** [Zombie](#zombie)

No data.


### Elder Guardian
**Extends:** [Guardian](#guardian)

No data.


### Fox
**Extends:** [Animal](#animal)

| Index | Data Type                                           | Field Name | Default                     |
|-------|-----------------------------------------------------|------------|-----------------------------|
| 18    | Integer                                             | TYPE       | Fox.Variant.DEFAULT.getId() |
| 19    | Byte                                                | FLAGS      | (byte)0                     |
| 20    | Optional&lt;EntityReference&lt;LivingEntity&gt;&gt; | TRUSTED_0  | Optional.empty()            |
| 21    | Optional&lt;EntityReference&lt;LivingEntity&gt;&gt; | TRUSTED_1  | Optional.empty()            |


### Frog
**Extends:** [Animal](#animal)

| Index | Data Type                 | Field Name    | Default                                                              |
|-------|---------------------------|---------------|----------------------------------------------------------------------|
| 18    | Holder&lt;FrogVariant&gt; | VARIANT       | VariantUtils.getDefaultOrAny(this.registryAccess(), DEFAULT_VARIANT) |
| 19    | OptionalInt               | TONGUE_TARGET | OptionalInt.empty()                                                  |


### Goat
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name        | Default |
|-------|-----------|-------------------|---------|
| 18    | Boolean   | IS_SCREAMING_GOAT | false   |
| 19    | Boolean   | HAS_LEFT_HORN     | true    |
| 20    | Boolean   | HAS_RIGHT_HORN    | true    |


### Happy Ghast
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name      | Default |
|-------|-----------|-----------------|---------|
| 18    | Boolean   | IS_LEASH_HOLDER | false   |
| 19    | Boolean   | STAYS_STILL     | false   |


### Hoglin
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name              | Default |
|-------|-----------|-------------------------|---------|
| 18    | Boolean   | IMMUNE_TO_ZOMBIFICATION | false   |


### Husk
**Extends:** [Zombie](#zombie)

No data.


### Magma Cube
**Extends:** [Abstract Cube Mob](#abstract-cube-mob)

No data.


### Ocelot
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 18    | Boolean   | TRUSTING   | false   |


### Panda
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name      | Default |
|-------|-----------|-----------------|---------|
| 18    | Integer   | UNHAPPY_COUNTER | 0       |
| 19    | Integer   | SNEEZE_COUNTER  | 0       |
| 20    | Integer   | EAT_COUNTER     | 0       |
| 21    | Byte      | MAIN_GENE       | (byte)0 |
| 22    | Byte      | HIDDEN_GENE     | (byte)0 |
| 23    | Byte      | FLAGS           | (byte)0 |


### Parched
**Extends:** [Abstract Skeleton](#abstract-skeleton)

No data.


### Pig
**Extends:** [Animal](#animal)

| Index | Data Type                     | Field Name    | Default                                                                                   |
|-------|-------------------------------|---------------|-------------------------------------------------------------------------------------------|
| 18    | Integer                       | BOOST_TIME    | 0                                                                                         |
| 19    | Holder&lt;PigVariant&gt;      | VARIANT       | VariantUtils.getDefaultOrAny(this.registryAccess(), PigVariants.DEFAULT)                  |
| 20    | Holder&lt;PigSoundVariant&gt; | SOUND_VARIANT | pigSoundVariants.get(PigSoundVariants.CLASSIC).or(pigSoundVariants::getAny).orElseThrow() |


### Piglin
**Extends:** [Abstract Piglin](#abstract-piglin)

| Index | Data Type | Field Name           | Default |
|-------|-----------|----------------------|---------|
| 17    | Boolean   | BABY                 | false   |
| 18    | Boolean   | IS_CHARGING_CROSSBOW | false   |
| 19    | Boolean   | IS_DANCING           | false   |


### Piglin Brute
**Extends:** [Abstract Piglin](#abstract-piglin)

No data.


### Polar Bear
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 18    | Boolean   | STANDING   | false   |


### Pufferfish
**Extends:** [Abstract Fish](#abstract-fish)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 17    | Integer   | PUFF_STATE | 0       |


### Rabbit
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name | Default                   |
|-------|-----------|------------|---------------------------|
| 18    | Integer   | TYPE       | Rabbit.Variant.DEFAULT.id |


### Raider
**Extends:** [Patrolling Monster](#patrolling-monster)

| Index | Data Type | Field Name     | Default |
|-------|-----------|----------------|---------|
| 16    | Boolean   | IS_CELEBRATING | false   |


### Sheep
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 18    | Byte      | WOOL       | (byte)0 |


### Skeleton
**Extends:** [Abstract Skeleton](#abstract-skeleton)

| Index | Data Type | Field Name       | Default |
|-------|-----------|------------------|---------|
| 16    | Boolean   | STRAY_CONVERSION | false   |


### Slime
**Extends:** [Abstract Cube Mob](#abstract-cube-mob)

No data.


### Sniffer
**Extends:** [Animal](#animal)

| Index | Data Type     | Field Name        | Default              |
|-------|---------------|-------------------|----------------------|
| 18    | Sniffer.State | STATE             | Sniffer.State.IDLING |
| 19    | Integer       | DROP_SEED_AT_TICK | 0                    |


### Squid
**Extends:** [Ageable Water Creature](#ageable-water-creature)

No data.


### Stray
**Extends:** [Abstract Skeleton](#abstract-skeleton)

No data.


### Strider
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name  | Default |
|-------|-----------|-------------|---------|
| 18    | Integer   | BOOST_TIME  | 0       |
| 19    | Boolean   | SUFFOCATING | false   |


### Sulfur Cube
**Extends:** [Abstract Cube Mob](#abstract-cube-mob)

| Index | Data Type | Field Name  | Default |
|-------|-----------|-------------|---------|
| 19    | Integer   | MAX_FUSE    | -1      |
| 20    | Boolean   | FROM_BUCKET | false   |


### Tadpole
**Extends:** [Abstract Fish](#abstract-fish)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 17    | Boolean   | AGE_LOCKED | false   |


### Tamable Animal
**Extends:** [Animal](#animal)

| Index | Data Type                                           | Field Name | Default          |
|-------|-----------------------------------------------------|------------|------------------|
| 18    | Byte                                                | FLAGS      | (byte)0          |
| 19    | Optional&lt;EntityReference&lt;LivingEntity&gt;&gt; | OWNERUUID  | Optional.empty() |


### Turtle
**Extends:** [Animal](#animal)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 18    | Boolean   | HAS_EGG    | false   |
| 19    | Boolean   | LAYING_EGG | false   |


### Villager
**Extends:** [Abstract Villager](#abstract-villager)

| Index | Data Type    | Field Name         | Default                     |
|-------|--------------|--------------------|-----------------------------|
| 19    | VillagerData | VILLAGER_DATA      | createDefaultVillagerData() |
| 20    | Boolean      | VILLAGER_FINALIZED | false                       |


### Wandering Trader
**Extends:** [Abstract Villager](#abstract-villager)

No data.


### Wither Skeleton
**Extends:** [Abstract Skeleton](#abstract-skeleton)

No data.


### Zombie Villager
**Extends:** [Zombie](#zombie)

| Index | Data Type    | Field Name         | Default                                   |
|-------|--------------|--------------------|-------------------------------------------|
| 19    | Boolean      | CONVERTING         | false                                     |
| 20    | VillagerData | VILLAGER_DATA      | initializeZombieVillagerData(this.random) |
| 21    | Boolean      | VILLAGER_FINALIZED | false                                     |


### Zombified Piglin
**Extends:** [Zombie](#zombie)

No data.


### Abstract Chested Horse
**Extends:** [Abstract Horse](#abstract-horse)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 19    | Boolean   | CHEST      | false   |


### Abstract Illager
**Extends:** [Raider](#raider)

No data.


### Abstract Nautilus
**Extends:** [Tamable Animal](#tamable-animal)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 20    | Boolean   | DASH       | false   |


### Camel
**Extends:** [Abstract Horse](#abstract-horse)

| Index | Data Type | Field Name            | Default |
|-------|-----------|-----------------------|---------|
| 19    | Boolean   | DASH                  | false   |
| 20    | Long      | LAST_POSE_CHANGE_TICK | 0L      |


### Cat
**Extends:** [Tamable Animal](#tamable-animal)

| Index | Data Type                     | Field Name      | Default                                                                                   |
|-------|-------------------------------|-----------------|-------------------------------------------------------------------------------------------|
| 20    | Holder&lt;CatVariant&gt;      | VARIANT         | VariantUtils.getDefaultOrAny(this.registryAccess(), DEFAULT_VARIANT)                      |
| 21    | Boolean                       | IS_LYING        | false                                                                                     |
| 22    | Boolean                       | RELAX_STATE_ONE | false                                                                                     |
| 23    | Integer                       | COLLAR_COLOR    | DEFAULT_COLLAR_COLOR.getId()                                                              |
| 24    | Holder&lt;CatSoundVariant&gt; | SOUND_VARIANT   | catSoundVariants.get(CatSoundVariants.CLASSIC).or(catSoundVariants::getAny).orElseThrow() |


### Cod
**Extends:** [Abstract Schooling Fish](#abstract-schooling-fish)

No data.


### Cow
**Extends:** [Abstract Cow](#abstract-cow)

| Index | Data Type                     | Field Name    | Default                                                                                   |
|-------|-------------------------------|---------------|-------------------------------------------------------------------------------------------|
| 18    | Holder&lt;CowVariant&gt;      | VARIANT       | VariantUtils.getDefaultOrAny(this.registryAccess(), CowVariants.TEMPERATE)                |
| 19    | Holder&lt;CowSoundVariant&gt; | SOUND_VARIANT | cowSoundVariants.get(CowSoundVariants.CLASSIC).or(cowSoundVariants::getAny).orElseThrow() |


### Glow Squid
**Extends:** [Squid](#squid)

| Index | Data Type | Field Name           | Default |
|-------|-----------|----------------------|---------|
| 18    | Integer   | DARK_TICKS_REMAINING | 0       |


### Horse
**Extends:** [Abstract Horse](#abstract-horse)

| Index | Data Type | Field Name   | Default |
|-------|-----------|--------------|---------|
| 19    | Integer   | TYPE_VARIANT | 0       |


### Mushroom Cow
**Extends:** [Abstract Cow](#abstract-cow)

| Index | Data Type | Field Name | Default                        |
|-------|-----------|------------|--------------------------------|
| 18    | Integer   | TYPE       | MushroomCow.Variant.DEFAULT.id |


### Ravager
**Extends:** [Raider](#raider)

No data.


### Salmon
**Extends:** [Abstract Schooling Fish](#abstract-schooling-fish)

| Index | Data Type | Field Name | Default                     |
|-------|-----------|------------|-----------------------------|
| 17    | Integer   | TYPE       | Salmon.Variant.DEFAULT.id() |


### Shoulder Riding Entity
**Extends:** [Tamable Animal](#tamable-animal)

No data.


### Skeleton Horse
**Extends:** [Abstract Horse](#abstract-horse)

No data.


### Tropical Fish
**Extends:** [Abstract Schooling Fish](#abstract-schooling-fish)

| Index | Data Type | Field Name   | Default                       |
|-------|-----------|--------------|-------------------------------|
| 17    | Integer   | TYPE_VARIANT | DEFAULT_VARIANT.getPackedId() |


### Witch
**Extends:** [Raider](#raider)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 17    | Boolean   | USING_ITEM | false   |


### Wolf
**Extends:** [Tamable Animal](#tamable-animal)

| Index | Data Type                      | Field Name     | Default                                                                                      |
|-------|--------------------------------|----------------|----------------------------------------------------------------------------------------------|
| 20    | Boolean                        | INTERESTED     | false                                                                                        |
| 21    | Integer                        | COLLAR_COLOR   | DEFAULT_COLLAR_COLOR.getId()                                                                 |
| 22    | Long                           | ANGER_END_TIME | -1L                                                                                          |
| 23    | Holder&lt;WolfVariant&gt;      | VARIANT        | VariantUtils.getDefaultOrAny(this.registryAccess(), WolfVariants.DEFAULT)                    |
| 24    | Holder&lt;WolfSoundVariant&gt; | SOUND_VARIANT  | wolfSoundVariants.get(WolfSoundVariants.CLASSIC).or(wolfSoundVariants::getAny).orElseThrow() |


### Zombie Horse
**Extends:** [Abstract Horse](#abstract-horse)

No data.


### Camel Husk
**Extends:** [Camel](#camel)

No data.


### Donkey
**Extends:** [Abstract Chested Horse](#abstract-chested-horse)

No data.


### Llama
**Extends:** [Abstract Chested Horse](#abstract-chested-horse)

| Index | Data Type | Field Name | Default |
|-------|-----------|------------|---------|
| 20    | Integer   | STRENGTH   | 0       |
| 21    | Integer   | VARIANT    | 0       |


### Mule
**Extends:** [Abstract Chested Horse](#abstract-chested-horse)

No data.


### Nautilus
**Extends:** [Abstract Nautilus](#abstract-nautilus)

No data.


### Parrot
**Extends:** [Shoulder Riding Entity](#shoulder-riding-entity)

| Index | Data Type | Field Name | Default                   |
|-------|-----------|------------|---------------------------|
| 20    | Integer   | VARIANT    | Parrot.Variant.DEFAULT.id |


### Pillager
**Extends:** [Abstract Illager](#abstract-illager)

| Index | Data Type | Field Name           | Default |
|-------|-----------|----------------------|---------|
| 17    | Boolean   | IS_CHARGING_CROSSBOW | false   |


### Spellcaster Illager
**Extends:** [Abstract Illager](#abstract-illager)

| Index | Data Type | Field Name    | Default |
|-------|-----------|---------------|---------|
| 17    | Byte      | SPELL_CASTING | (byte)0 |


### Vindicator
**Extends:** [Abstract Illager](#abstract-illager)

No data.


### Zombie Nautilus
**Extends:** [Abstract Nautilus](#abstract-nautilus)

| Index | Data Type                           | Field Name | Default                                                                               |
|-------|-------------------------------------|------------|---------------------------------------------------------------------------------------|
| 21    | Holder&lt;ZombieNautilusVariant&gt; | VARIANT    | VariantUtils.getDefaultOrAny(this.registryAccess(), ZombieNautilusVariants.TEMPERATE) |


### Evoker
**Extends:** [Spellcaster Illager](#spellcaster-illager)

No data.


### Illusioner
**Extends:** [Spellcaster Illager](#spellcaster-illager)

No data.


### Trader Llama
**Extends:** [Llama](#llama)

No data.

