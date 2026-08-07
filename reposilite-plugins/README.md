# Plugins
Basic plugins for Ingot, maintained with the core project.

* [example-plugin](./example-plugin) - Basic template of an Ingot plugin in Java with Gradle KTS configuration (preferred over Groovy script file).
* [groovy-plugin](./groovy-plugin) - Utility plugin that enables scripting API in Groovy. In general, you should probably use Java/Kotlin for better experience and performance, but that's still an option - especially useful for quick prototyping of dirty scripts.
* [javadoc-plugin](./groovy-plugin) - This plugin serves `-javadoc.jar` files as Java docs pages mounted in `/javadoc/<gav>` path  
* [migration-plugin](./migration-plugin) - Converts old `tokens.dat` file from Reposilite 2.x into the new json scheme used by 3.x 
* [swagger-plugin](./swagger-plugin) - Exposes Swagger UI `/swagger` for builtin OpenApi `/openapi` endpoint 

If you're interested in extending Ingot with a new plugin, you should probably create a new repository.
Let us know about your work, so we can list your project below!

### 3rd plugins

* ~ None ~