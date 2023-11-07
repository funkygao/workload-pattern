BRANCH := $(shell git rev-parse --abbrev-ref HEAD)

package:clean
	@mvn package

clean:
	@mvn clean

install:clean test
	@mvn install

test:
	@mvn test -Ptest

coverage:clean
	@mvn clean verify -Ptest

javadoc:
	@mvn javadoc:javadoc -Prelease
	@open target/site/apidocs/index.html
