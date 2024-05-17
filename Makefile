BRANCH := $(shell git rev-parse --abbrev-ref HEAD)

define visualize_cmd
    @cat test/log | grep -w cpu | python doc/shed_visualize.py
endef

package:clean
	@mvn package

clean:
	@mvn clean
	@rm -f test/*.bench test/*.html test/*.jfr test/log

install:clean test
	@mvn install

deploy:clean
	@mvn verify
	@mvn deploy

benchmark:
	@cat test/*.bench

test:clean
	@mvn test -Ptest

coverage:install
	@mvn clean verify -Ptest
	@open test/target/site/jacoco-aggregate/index.html

javadoc:install
	@mvn javadoc:javadoc -Prelease
	@open target/site/apidocs/index.html

encapsulation:
	@mvn io.github.dddplus:dddplus-maven-plugin:model -DrootDir=./overload-control -Dencapsulation=./doc/encapsulation.txt -X

flamegraph:
	@java -cp $(ASYNC_PROFILER_HOME)/lib/converter.jar jfr2flame test/pf.jfr test/flamegraph.html
	@open test/flamegraph.html

simulation-overload-case-normal:
	@rm -f test/log
	@mvn -Dtest=io.github.workload.overloading.OverloadSimulationTest#normal_case_http_only -Dsimulate=true -Dsurefire.failIfNoSpecifiedTests=false test
	@$(visualize_cmd)

visualize:
	$(visualize_cmd)
