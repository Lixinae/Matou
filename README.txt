/---------------------------------\
|-------Format des dossiers-------|
\---------------------------------/

----------------------------------------
| Avant utilisation de la commande ant |
----------------------------------------

Format du dossier du base avant utilisation ant : 

docs/ : Contient les pdf user_manual et dev_manual ainsi que la RFC
exe/ : Contient les executable du serveur et du client compilé.
src/ : Contient les sources du projet

build.xml : Commandes ant pour la compilation et la generation des .jar
build.properties

README.txt : Ce fichier explique toute la composition du dossier du projet


----------------------------------------
| Après utilisation de la commande ant |
----------------------------------------

classes/ : Dossiers des fichiers compilé

dest/ : Contient les dossiers des executable Client et Server
dest/Server : Dossier de l'executable Server ainsi que son fichier MANIFEST
dest/Client : Dossier de l'executable Client ainsi que son fichier MANIFEST
docs/api : Contient la javadoc genere

Le reste du dossier ne change pas.



/------------------------------------\
|------------- Execution ------------|
\------------------------------------/

------------------------------------
|	  Generation des éléments	   |
------------------------------------
Pour generer/supprimer les executable et la javadoc il faut utliser les commandes :
	- ant 
	- ant all ( equivalent à "ant" ) : Compile et genere la javadoc ainsi que les executable
	- ant clean : Nettoie tout le dossier , supprime le dossier des executables , ainsi que la doc et les fichiers compilé.
	

------------------------------------
|	  Execution du programme	   |
------------------------------------	
Une fois compilé , il suffit de lancer les commandes suivantes :
	- java -jar ./dest/Server/Server-1.0.jar : Pour lancer le serveur à partir du dossier racine . Ceci chargera les valeurs par default pour l'execution.	
	- java -jar ./dest/Client/Client-1.0.jar : Pour lancer le client à partir du dossier racine . Ceci chargera les valeurs par default pour l'execution.
	
On peut egalement les lancer avec des arguments :
Il faut etre situé dans le dossier "classes" au moment de l'ecriture de ces lignes :
	- java matou.server.Server [port] : Lance le serveur sur le "port" donné en arguments , s'il n'y en a pas , le port par default sera : 7777
	- java matou.client.Client [host] [port] :  Lance le client qui se connecte sur le "host" sur le "port" donné en arguments.
												S'il n'y pas d'arguments , les valeurs par default sont host = "localhost" et port = "7777"
