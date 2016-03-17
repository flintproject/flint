/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: t; c-basic-offset: 4 -*- vim:set ts=4 sw=4 sts=4 noet: */
#ifndef FLINT_RUNTIME_SECTION_CONTEXT_H_
#define FLINT_RUNTIME_SECTION_CONTEXT_H_

#include "bc.pb.h"

namespace flint {
namespace runtime {

void ReportSectionContext(const bc::SectionHeader &sh);

}
}

#endif
