import React, {useState} from "react";
import {useTranslation} from "react-i18next";
import {connect} from "react-redux";
import GeneralDetailsTab from "../wizards/GeneralDetailsTab";
import ConfigurationDetailsTab from "../wizards/ConfigurationDetailsTab";
import CapabilitiesDetailsTab from "../wizards/CapabilitiesDetailsTab";
import {getRecordingDetails} from "../../../../selectors/recordingDetailsSelectors";
import ModalNavigation from "../../../shared/modals/ModalNavigation";

/**
 * This component manages the pages of the recording details
 */
const RecordingsDetails = ({ agent }) => {
    const { t } = useTranslation();

    const [page, setPage] = useState(0);

    // information about tabs
    const tabs = [
        {
            tabNameTranslation: 'RECORDINGS.RECORDINGS.DETAILS.TAB.GENERAL',
            name: 'general'
        },
        {
            tabNameTranslation: 'RECORDINGS.RECORDINGS.DETAILS.TAB.CONFIGURATION',
            name: 'configuration'
        },
        {
            tabNameTranslation: 'RECORDINGS.RECORDINGS.DETAILS.TAB.CAPABILITIES',
            name: 'capabilities'
        }
    ];

    const openTab = tabNr => {
        setPage(tabNr);
    }

    return (
        <>
            {/* navigation */}
            <ModalNavigation tabInformation={tabs}
                             openTab={openTab}
                             page={page}/>

            <div>
                {page === 0 && (
                    <GeneralDetailsTab agent={agent}/>
                )}
                {page === 1 && (
                    <ConfigurationDetailsTab agent={agent}/>
                )}
                {page === 2 && (
                    <CapabilitiesDetailsTab agent={agent}/>
                )}
            </div>
        </>
    );
};

// get current state out of redux store
const mapStateToProps = state => ({
    agent: getRecordingDetails(state)
});

export default connect(mapStateToProps)(RecordingsDetails);
