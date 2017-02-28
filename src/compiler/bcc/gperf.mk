GENERATED_SOURCES = function.cc procedure.cc

.PHONY: all clean

all: $(GENERATED_SOURCES)

clean:
	-rm -f $(GENERATED_SOURCES)

%.cc: %.txt
	gperf $< > $@
