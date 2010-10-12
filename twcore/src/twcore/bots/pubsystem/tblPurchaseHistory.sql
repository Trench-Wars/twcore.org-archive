CREATE TABLE IF NOT EXISTS `tblPurchaseHistory` (
  `fnPurchaseId` int(11) NOT NULL AUTO_INCREMENT,
  `fcItemName` varchar(30) NOT NULL,
  `fcBuyerName` varchar(23) NOT NULL,
  `fcReceiverName` varchar(23) NOT NULL,
  `fcArguments` varchar(30) NOT NULL,
  `fnPrice` int(11) NOT NULL,
  `fnReceiverShipType` int(11) NOT NULL,
  `fdDate` datetime NOT NULL,
  PRIMARY KEY (`fnPurchaseId`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 AUTO_INCREMENT=3 ;