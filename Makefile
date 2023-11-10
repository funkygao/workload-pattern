BRANCH := $(shell git rev-parse --abbrev-ref HEAD)

package:clean
	@mvn package

clean:
	@mvn clean

install:clean test
	@mvn install

deploy:clean
	@mvn clean deploy verify

test:
	@mvn test -Ptest

coverage:clean
	@mvn clean verify -Ptest

javadoc:
	@mvn javadoc:javadoc -Prelease
	@open target/site/apidocs/index.html

encapsulation:
	@mvn io.github.dddplus:dddplus-maven-plugin:model -DrootDir=./overload-control -Dencapsulation=./doc/encapsulation.txt -X
