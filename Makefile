DEST = $(PWD)/build
make:
	cd src && javac -cp . hydna/*.java hydna/examples/*.java -d $(DEST)

hello:
	cd $(DEST) && java hydna.examples.HelloWorld
