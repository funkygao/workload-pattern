SHELL := /bin/bash
.SILENT:
.PHONY: package clean javadoc test install

BRANCH := $(shell git rev-parse --abbrev-ref HEAD)

define visualize_cmd
    @cat test/log | grep -w cpu | head -1800 | python doc/shed_visualize.py
endef

help:
	awk 'BEGIN {FS = ":.*##"; printf "Usage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } /^##[^@]/ { printf "%s\n", substr($$0, 4) }' $(MAKEFILE_LIST)

##@ Build

package:clean ## Package
	mvn package

clean_generated:
	rm -f test/*.bench test/*.html test/*.jfr test/log

clean:clean_generated
	mvn clean

install:clean test
	mvn install

javadoc:install ## Generate javadoc
	mvn javadoc:javadoc -Prelease
	open target/site/apidocs/index.html

benchmark: ## Show the benchmark result
	cat test/*.bench

test:clean
	$(MAKE) -C stream-simd build
	mvn test -Ptest

coverage:install ## Run unit test and show coverage report
	mvn clean verify -Ptest
	open test/target/site/jacoco-aggregate/index.html

deploy:clean ## Deploy to local maven repo
	mvn verify
	mvn deploy

encapsulation:
	mvn io.github.dddplus:dddplus-maven-plugin:model -DrootDir=./overload-control -Dencapsulation=./doc/encapsulation.txt -X

flamegraph:
	java -cp $(ASYNC_PROFILER_HOME)/lib/converter.jar jfr2flame test/pf.jfr test/flamegraph.html
	open test/flamegraph.html

##@ Simulation

overload-busy:clean_generated ## Simulate busy web site
	mvn -Dtest=io.github.workload.overloading.OverloadSimulationTest#case_continuous_busy -Dsimulate=true -Dsurefire.failIfNoSpecifiedTests=false test
	$(visualize_cmd)

overload-jitter:clean_generated ## Simulate jitted load web site
	mvn -Dtest=io.github.workload.overloading.OverloadSimulationTest#case_lazy_jitter -Dsimulate=true -Dsurefire.failIfNoSpecifiedTests=false test
	$(visualize_cmd)

overload-greedy:clean_generated ## Simulate greedy payload web site
	mvn -Dtest=io.github.workload.overloading.OverloadSimulationTest#case_idle_greedy -Dsimulate=true -Dsurefire.failIfNoSpecifiedTests=false test
	$(visualize_cmd)

visualize: ## Visualize the result
	$(visualize_cmd)

