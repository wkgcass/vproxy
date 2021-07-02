.PHONY: clean
clean:
	rm -f .libbpf
	cd libbpf/src && make clean
	rm -f sample_user
	rm -f sample_kern.ll
	rm -f sample_kern.o

.libbpf:
	cd libbpf/src && make
	touch .libbpf

.PHONY: libbpf
libbpf: .libbpf

sample_kern.o:
	clang -O2 -target bpf -S -c -emit-llvm -o sample_kern.ll sample_kern.c
	llc -march=bpf -filetype=obj -o sample_kern.o sample_kern.ll

.PHONY: kern
kern:
	rm -f sample_kern.ll
	rm -f sample_kern.o
	make sample_kern.o

sample_user: .libbpf
	gcc -O2 -L./libbpf/src -I./libbpf/src -g -o sample_user sample_user.c vproxy_xdp.c vproxy_xdp_util.c -lbpf

.PHONY: user
user:
	rm -f sample_user
	make sample_user

.PHONY: run
run: sample_kern.o sample_user
	LD_LIBRARY_PATH=./libbpf/src ./sample_user $(filter-out $@,$(MAKECMDGOALS))
