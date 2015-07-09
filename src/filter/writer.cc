/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: t; c-basic-offset: 4 -*- vim:set ts=4 sw=4 sts=4 noet: */
#include "writer.hh"

#include <cassert>
#include <iostream>

using std::cerr;
using std::endl;

namespace filter {

namespace {

void Compact(const std::map<int, int> &m, std::vector<std::pair<size_t, size_t> > *v)
{
	auto it = m.cbegin();
	while (it != m.cend()) {
		int p = it->first;
		int s = it->second;
		while (++it != m.cend()) {
			assert(p + s <= it->first);
			if (p + s < it->first)
				break;
			s += it->second;
		}
		v->emplace_back(std::make_pair(size_t(p), size_t(s)));
	}
}

}

Writer::Writer(const std::map<int, int> &m)
	: v_()
{
	Compact(m, &v_);
}

bool Writer::Write(const double *data, FILE *fp) const
{
	for (auto const &p : v_) {
		if (fwrite(data + p.first, sizeof(double), p.second, fp) != p.second) {
			cerr << "failed to filter output" << endl;
			return false;
		}
	}
	return true;
}

}
