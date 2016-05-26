#!/bin/bash

source=$1
target=$2

webtmp1=webtmp1.xml
webtmp2=webtmp2.xml
filter=filter-web.xml
filtermapping=filter-mapping-web.xml

test -z "$source" && echo "You must specify a source file" && exit 1
test -z "$target" && echo "You must specify a target conf directory" && exit 1

test ! -f "$source" && echo "Source $source does not exist or is not a file" && exit 1
test ! -f "$target" && echo "Target directory $2 does not exist or is not a directory" && exit 1
test ! -f "$target" && echo "File web.xml does not exist or is not a file in directory $2/conf/" && exit 1

if [ -n "$( grep 'CORS Filter End' $target )" ]; then
    sed "/CORS Filter End/r $source" $target > $temp
else
	sed -n '/<filter>/,/<\/filter>/p' $source > $filter
	# sed -n '/<filter>/,/<\/filter>/p' $source | tr -d '\t' | tr -d '\n' > $filter
	sed -n '/<filter-mapping>/,/<\/filter-mapping>/p' $source > $filtermapping
	# sed -n '/<filter-mapping>/,/<\/filter-mapping>/p' $source | tr -d '\t' | tr -d '\n' > $filtermapping
	
	sed "/Built In Filter Mappings/r $filter" $target > $webtmp1
	sed "/Default Session Configuration/r $filtermapping" $webtmp1 > $webtmp2
	# sed "/Built In Filter Mappings/i $(cat $filter)" $target > $webtmp1
	# sed "/Default Session Configuration/i $(cat $filtermapping)" $webtmp1 > $webtmp2
fi

cp $target $target.orig
cp $webtmp2 $target