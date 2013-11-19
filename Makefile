DEST = $(PWD)/build
make:
	mkdir -p $(DEST)
	cd src/main/java/com && javac -cp . hydna/*.java hydna/examples/*.java -d $(DEST)

hello:
	cd $(DEST) && java com.hydna.examples.HelloWorld
