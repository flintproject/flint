/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: t; c-basic-offset: 4 -*- vim:set ts=4 sw=4 sts=4 noet: */
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "load/param.h"

#include <boost/uuid/uuid_io.hpp>

#include "bc/pack.h"
#include "cellml.h"
#include "database.h"
#include "lo.pb.h"
#include "phml.h"
#include "sbml.h"

#define BOOST_TEST_MODULE test_param
#include "test.h"

struct F : public test::MemoryFixture {

	void Cellml(const char *file) {
		SaveGivenFile(driver_.db(), file);
		BOOST_REQUIRE(cellml::Read(driver_.db()));
	}

	void Phml(const char *file) {
		SaveGivenFile(driver_.db(), file);
		BOOST_REQUIRE(phml::Read(driver_.db(), boost::filesystem::current_path()));
	}

	void Sbml(const char *file) {
		SaveGivenFile(driver_.db(), file);
		BOOST_REQUIRE(sbml::Read(driver_.db()));
	}

	void GenerateAndCompare(const char *output, const char *input)
	{
		BOOST_REQUIRE(load::Param(driver_.db(), output));

		boost::filesystem::path fp(__FILE__);
		boost::filesystem::path ep = fp.parent_path();
		ep /= "param";
		ep /= input;

		boost::filesystem::path op(std::string(output) + ".txt");
		boost::filesystem::ofstream ofs(op, std::ios::binary);
		BOOST_REQUIRE(ofs);
		std::ifstream ifs(output, std::ios::binary);
		BOOST_REQUIRE(ifs);
		lo::Header header;
		BOOST_REQUIRE(UnpackFromIstream(header, &ifs));
		lo::Column column;
		while (UnpackFromIstream(column, &ifs)) {
			boost::uuids::uuid uuid;
			std::memcpy(&uuid, column.uuid().c_str(), uuid.size());
			ofs << column.position()
				<< ' '
				<< uuid
				<< ' '
				<< column.id()
				<< ' '
				<< column.name()
				<< std::endl;
		}
		ifs.close();
		ofs.close();

		test::CheckSame(op, ep);
		boost::filesystem::remove(op);
		std::remove(output);
	}
};

BOOST_FIXTURE_TEST_SUITE(test_param, F)

BOOST_AUTO_TEST_CASE(lockwood_ewy_hermann_holford_2006) {
	Cellml(TEST_MODELS("lockwood_ewy_hermann_holford_2006.cellml"));
	GenerateAndCompare("lockwood_ewy_hermann_holford_2006.cellml.param",
					   "lockwood_ewy_hermann_holford_2006.cellml.txt");
}

BOOST_AUTO_TEST_CASE(yang_tong_mccarver_hines_beard_2006) {
	Cellml(TEST_MODELS("yang_tong_mccarver_hines_beard_2006.cellml"));
	GenerateAndCompare("yang_tong_mccarver_hines_beard_2006.cellml.param",
					   "yang_tong_mccarver_hines_beard_2006.cellml.txt");
}

BOOST_AUTO_TEST_CASE(Rybak_2006_with_static_instance_and_multiple_input) {
	Phml(TEST_MODELS("Rybak_2006_with_static_instance_and_multiple_input.isml"));
	// GenerateAndCompare("Rybak_2006_with_static_instance_and_multiple_input.isml.param",
	// 				   "Rybak_2006_with_static_instance_and_multiple_input.isml.txt");
}

BOOST_AUTO_TEST_CASE(hepatocyte_external) {
	Phml(TEST_MODELS("hepatocyte_external.isml"));
	boost::filesystem::remove("37a676f5-5197-40fc-ae9f-5d5fdb921fba.db");
	GenerateAndCompare("hepatocyte_external.isml.param",
					   "hepatocyte_external.isml.txt");
}

BOOST_AUTO_TEST_CASE(hepatocyte_internal) {
	Phml(TEST_MODELS("hepatocyte_internal.isml"));
	boost::filesystem::remove("4d96c8de-d10a-48e2-a0e0-be9d74e58e78.db");
	boost::filesystem::remove("4d96c8de-d10a-48e2-a0e0-be9d74e58e78.xml");
	GenerateAndCompare("hepatocyte_internal.isml.param",
					   "hepatocyte_internal.isml.txt");
}

BOOST_AUTO_TEST_CASE(ringed_Beeler_Reuter_1977_model_with_static_instance) {
	Phml(TEST_MODELS("ringed_Beeler_Reuter_1977_model_with_static_instance.isml"));
	// GenerateAndCompare("ringed_Beeler_Reuter_1977_model_with_static_instance.isml.param",
	// 				   "ringed_Beeler_Reuter_1977_model_with_static_instance.isml.txt");
}

BOOST_AUTO_TEST_CASE(BIOMD0000000114) {
	Sbml(TEST_MODELS("BIOMD0000000114.xml"));
	GenerateAndCompare("BIOMD0000000114.xml.param",
					   "BIOMD0000000114.xml.txt");
}

BOOST_AUTO_TEST_CASE(BIOMD0000000152) {
	Sbml(TEST_MODELS("BIOMD0000000152.xml"));
	GenerateAndCompare("BIOMD0000000152.xml.param",
					   "BIOMD0000000152.xml.txt");
}

BOOST_AUTO_TEST_SUITE_END()
