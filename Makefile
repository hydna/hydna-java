DEST = $(PWD)/build
make:
	mkdir -p build
	cd src/main/java/com && javac -cp . hydna/*.java hydna/examples/*.java -d $(DEST)

hello:
	cd $(DEST) && java hydna.examples.HelloWorld
