CREATE TABLE IF NOT EXISTS `tblPlayerDonations` (
  `fnDonationId` int(11) NOT NULL AUTO_INCREMENT,
  `fcName` varchar(23) NOT NULL,
  `fcNameTo` varchar(23) NOT NULL,
  `fnMoney` int(11) NOT NULL,
  `fdDate` datetime NOT NULL,
  PRIMARY KEY (`fnDonationId`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 AUTO_INCREMENT=3 ;