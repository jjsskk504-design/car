rootProject.name = "engine02-prism-contract-check"
includeBuild("..") {
    dependencySubstitution {
        substitute(module("ai.riskradar:engine02-kotlin")).using(project(":"))
    }
}
