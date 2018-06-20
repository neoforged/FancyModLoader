# Forge Service Provider Interfaces

Service providers, and other global API-like elements,
separated so dependencies don't need to be on the core forge system.

* ```net.minecraftforge.api.distmarker.*``` are the ```Dist```/```OnlyIn``` annotation pair, used 
to identify elements that are only present in one of the two common distributions.
* ```net.minecraftforge.forgespi.ICoreMod*``` are interfaces for communication between
Forge and the coremod library.
