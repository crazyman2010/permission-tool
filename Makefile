all:
	rm -f library/build/outputs/aar/*
	gradle aR
	cp library/build/outputs/aar/*.aar .

clean:
	gradle clean
