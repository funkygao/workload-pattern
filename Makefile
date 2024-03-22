BRANCH := $(shell git rev-parse --abbrev-ref HEAD)

package:clean
	@mvn package

clean:
	@mvn clean
	@rm -f test/*.bench

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
