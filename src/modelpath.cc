/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: t; c-basic-offset: 4 -*- vim:set ts=4 sw=4 sts=4 noet: */
#include "modelpath.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include <boost/scoped_array.hpp>

#include "database.h"
#include "utf8path.h"

using std::memcpy;

namespace {

typedef int (*FindFunction)(sqlite3 *, char *);

char *GetInputFilename(sqlite3 *db, FindFunction f)
{
	boost::scoped_array<char> utf8(new char[1024]); // FIXME
	if (!f(db, utf8.get())) {
		std::exit(EXIT_FAILURE);
	}
	boost::filesystem::path path = GetPathFromUtf8(utf8.get());
	std::string path_s = path.string();
	size_t s = path_s.size();
	char *filename = new char[s+1]; // FIXME
	if (s > 0)
		memcpy(filename, path_s.c_str(), s);
	filename[s] = '\0';
	return filename;
}

} // namespace

char *GetGivenFilename(sqlite3 *db)
{
	return GetInputFilename(db, FindGivenFile);
}

char *GetModelFilename(sqlite3 *db)
{
	return GetInputFilename(db, FindModelFile);
}
